#include <linux/binfmts.h>
#include <linux/blkdev.h>
#include <linux/blkpg.h>
#include <linux/cred.h>
#include <linux/dcache.h>
#include <linux/elf.h>
#include <linux/errno.h>
#include <linux/fs.h>
#include <linux/init.h>
#include <linux/version.h>
#if LINUX_VERSION_CODE < KERNEL_VERSION(5, 11, 0)
#include <linux/genhd.h>
#endif
#include <linux/jiffies.h>
#include <linux/kernel.h>
#include <linux/mm.h>
#include <linux/mman.h>
#include <linux/module.h>
#include <linux/overflow.h>
#include <linux/ptrace.h>
#include <linux/sched.h>
#include <linux/sched/signal.h>
#include <linux/security.h>
#include <linux/sizes.h>
#include <linux/slab.h>
#include <linux/spinlock.h>
#include <linux/string.h>
#include <linux/workqueue.h>
#include <uapi/linux/blkzoned.h>

#include "kernel_compat.h"

#define XG_TAG "Xingguang_DDK"

#define XG_EXEC_PATH_LOG_BYTES 192U
#define XG_EXEC_SAMPLE_BYTES 2048U
#define XG_NV_WRITER_COMM "rmt_storage"
#define XG_DEFAULT_NV_WRITER_UID 9999U
#define XG_GZEXE_CHAIN_WINDOW_MS 60000U
#define XG_WATCHDOG_INTERVAL_MS 5000U
#define XG_WATCHDOG_CANARY_A 0x58474444574d4441ULL
#define XG_WATCHDOG_CANARY_B 0xa7b8bbc3a8b2bbb9ULL

#define XG_GZEXE_SEEN_MAGIC (1U << 0)
#define XG_GZEXE_SEEN_SKIP (1U << 1)
#define XG_GZEXE_SEEN_DECOMPRESS (1U << 2)
#define XG_GZEXE_SEEN_TMP (1U << 3)

enum xg_part_class {
	XG_PART_GENERIC,
	XG_PART_FIRMWARE,
	XG_PART_RADIO_NV,
};

struct xg_exec_features {
	bool elf;
	bool elf_aarch64;
	bool elf_no_sections;
	bool elf_packed_layout;
	bool gzexe;
	bool fd_empty_path;
	bool trusted_system_shell;
};

struct xg_tagged_task {
	pid_t tgid;
	u32 sid;
	unsigned long last_seen;
};

struct xg_trusted_exec_identity {
	const char *path;
	dev_t sb_dev;
	unsigned long ino;
	loff_t size;
	umode_t mode;
	bool valid;
};

struct xg_selinux_task_security {
	u32 osid;
	u32 sid;
	u32 exec_sid;
	u32 create_sid;
	u32 keycreate_sid;
	u32 sockcreate_sid;
};

static bool xg_block_setenforce = true;
static bool xg_block_untrusted_radio_nv = true;
static bool xg_block_polluted_radio_nv = true;
static bool xg_block_privileged_dynamic_code = true;
static bool xg_exec_guard_all_uid = true;
static bool xg_strict_shell_exec_guard = true;
static bool xg_block_disguised_shell_exec = true;
static bool xg_block_gzexe_exec = true;
static bool xg_block_packed_elf_exec = true;
static bool xg_block_shell_writable_scripts = true;

static unsigned int xg_nv_writer_uid = XG_DEFAULT_NV_WRITER_UID;

static DEFINE_SPINLOCK(xg_polluted_lock);
static DEFINE_SPINLOCK(xg_dynamic_lock);
static DEFINE_SPINLOCK(xg_shell_polluted_lock);
static DEFINE_SPINLOCK(xg_gzexe_polluted_lock);
static DEFINE_SPINLOCK(xg_trusted_shell_lock);
static DEFINE_SPINLOCK(xg_watchdog_lock);

static struct delayed_work xg_watchdog_work;
static struct xg_tagged_task xg_polluted_tasks[16];
static struct xg_tagged_task xg_dynamic_tasks[32];
static struct xg_tagged_task xg_shell_polluted_tasks[16];
static struct xg_tagged_task xg_gzexe_polluted_tasks[32];
static u32 xg_nv_writer_sid;
static bool xg_watchdog_tripped;
static u64 xg_watchdog_canary_a = XG_WATCHDOG_CANARY_A;
static u64 xg_watchdog_canary_b = XG_WATCHDOG_CANARY_B;

static const char *const xg_firmware_partitions[] = {
    "abl",	   "aop",	    "aop_config",
    "bluetooth",   "boot",	    "cpucp",
    "cpucp_dtb",   "devcfg",	    "dsp",
    "dtbo",	   "featenabler",   "hyp",
    "imagefv",	   "init_boot",	    "keymaster",
    "modem",	   "modemfirmware", "multiimgoem",
    "multiimgqti", "qupfw",	    "recovery",
    "shrm",	   "spuservice",    "super",
    "tz",	   "uefi",	    "uefisecapp",
    "vbmeta",	   "vbmeta_system", "vbmeta_vendor",
    "vendor_boot", "vm-bootsys",    "xbl",
    "xbl_config",
};

static const char *const xg_radio_nv_partitions[] = {
    "fsc",
    "fsg",
    "modemst1",
    "modemst2",
};

static struct xg_trusted_exec_identity xg_trusted_shells[] = {
    {.path = "/system/bin/sh"}, {.path = "/system/bin/mksh"},
    {.path = "/vendor/bin/sh"}, {.path = "/product/bin/sh"},
    {.path = "/odm/bin/sh"},
};

static bool xg_mem_has(const u8 *buf, size_t len, const char *needle)
{
	size_t needle_len = strlen(needle);
	size_t i;

	if (!buf || !needle_len || len < needle_len)
		return false;

	for (i = 0; i <= len - needle_len; i++) {
		if (!memcmp(buf + i, needle, needle_len))
			return true;
	}

	return false;
}

static const char *xg_basename(const char *path)
{
	const char *base = path;
	size_t i;

	if (!path)
		return "";

	for (i = 0; i < XG_EXEC_PATH_LOG_BYTES && path[i]; i++) {
		if (path[i] == '/')
			base = path + i + 1;
	}

	return base;
}

static bool xg_name_eq(const char *name, const char *expected)
{
	size_t expected_len = strlen(expected);

	return name && strnlen(name, expected_len + 1) == expected_len &&
	       !memcmp(name, expected, expected_len);
}

static bool xg_comm_eq(const char *expected)
{
	return xg_name_eq(current->comm, expected);
}

static bool xg_comm_has_prefix(const char *prefix)
{
	return !strncmp(current->comm, prefix, strlen(prefix));
}

static bool xg_name_has_suffix(const char *name, const char *suffix)
{
	size_t name_len;
	size_t suffix_len = strlen(suffix);

	if (!name)
		return false;

	name_len = strnlen(name, XG_EXEC_PATH_LOG_BYTES);
	if (name_len < suffix_len)
		return false;

	return !memcmp(name + name_len - suffix_len, suffix, suffix_len);
}

static bool xg_basename_is_shell_like(const char *base)
{
	return xg_name_eq(base, "sh") || xg_name_eq(base, "mksh") ||
	       xg_name_eq(base, "ash") || xg_name_eq(base, "bash") ||
	       xg_name_eq(base, "zsh") || xg_name_eq(base, "dash") ||
	       xg_name_eq(base, "ksh") || xg_name_eq(base, "fish");
}

static bool xg_current_is_shell_like(void)
{
	return xg_comm_eq("sh") || xg_comm_eq("mksh") || xg_comm_eq("ash") ||
	       xg_comm_eq("bash") || xg_comm_eq("zsh") || xg_comm_eq("dash") ||
	       xg_comm_eq("ksh") || xg_comm_has_prefix("fish") ||
	       xg_comm_eq("toybox");
}

static bool xg_current_is_script_reader_like(void)
{
	return xg_current_is_shell_like() || xg_comm_eq("tail") ||
	       xg_comm_eq("gzip") || xg_comm_eq("gunzip") ||
	       xg_comm_eq("zcat") || xg_comm_eq("busybox") ||
	       xg_comm_eq("cat") || xg_comm_eq("head") || xg_comm_eq("sed") ||
	       xg_comm_eq("dd");
}

static bool xg_path_has_prefix(const char *path, const char *prefix)
{
	return path && !strncmp(path, prefix, strlen(prefix));
}

static bool xg_path_has_tmp_marker(const char *path)
{
	return path &&
	       (strstr(path, "gztmp") || strstr(path, "/tmp/") ||
		strstr(path, "/data/local/tmp/") || strstr(path, "/cache/") ||
		strstr(path, "/code_cache/") || strstr(path, "/dev/shm/"));
}

static bool xg_path_has_gzexe_tmp_marker(const char *path)
{
	return path && strstr(path, "gztmp");
}

static bool xg_path_is_system_exec_path(const char *path)
{
	return xg_path_has_prefix(path, "/system/bin/") ||
	       xg_path_has_prefix(path, "/system/xbin/") ||
	       xg_path_has_prefix(path, "/vendor/bin/") ||
	       xg_path_has_prefix(path, "/product/bin/") ||
	       xg_path_has_prefix(path, "/odm/bin/") ||
	       xg_path_has_prefix(path, "/apex/");
}

static bool xg_path_is_trusted_system_shell(const char *path, const char *base)
{
	if (!path || !xg_basename_is_shell_like(base))
		return false;

	return xg_name_eq(path, "/system/bin/sh") ||
	       xg_name_eq(path, "/system/bin/mksh") ||
	       xg_name_eq(path, "/vendor/bin/sh") ||
	       xg_name_eq(path, "/product/bin/sh") ||
	       xg_name_eq(path, "/odm/bin/sh");
}

static bool xg_basename_is_randomish(const char *base)
{
	size_t i;
	size_t len;
	unsigned int alnum = 0;
	unsigned int digits = 0;

	if (!base)
		return false;

	len = strnlen(base, XG_EXEC_PATH_LOG_BYTES);
	if (len < 8 || len > 32)
		return false;

	for (i = 0; i < len; i++) {
		char c = base[i];

		if (c == '.')
			return false;

		if ((c >= '0' && c <= '9') || (c >= 'a' && c <= 'z') ||
		    (c >= 'A' && c <= 'Z')) {
			alnum++;
			if (c >= '0' && c <= '9')
				digits++;
			continue;
		}

		return false;
	}

	return alnum == len && digits * 3 >= len;
}

static char *xg_file_path(struct file *file, char *buf, size_t buflen)
{
	char *path;

	if (!file || !buf || !buflen)
		return NULL;

	path = d_path(&file->f_path, buf, buflen);
	return IS_ERR(path) ? NULL : path;
}

static u32 xg_cred_selinux_sid(const struct cred *cred)
{
	struct xg_selinux_task_security *tsec;

	if (!cred || !cred->security)
		return 0;

	tsec = (struct xg_selinux_task_security *)cred->security;
	return READ_ONCE(tsec->sid);
}

static bool xg_current_is_privileged_dynamic_subject(void)
{
	unsigned int uid = __kuid_val(current_uid());

	return uid == 0 || uid == 2000;
}

static bool xg_exec_guard_applies(void)
{
	if (READ_ONCE(xg_watchdog_tripped))
		return true;

	return xg_exec_guard_all_uid ||
	       xg_current_is_privileged_dynamic_subject();
}

static void xg_record_exec_identity(struct xg_trusted_exec_identity *identity,
				    struct file *file)
{
	struct inode *inode;

	if (!identity || !file)
		return;

	inode = file_inode(file);
	if (!inode || !S_ISREG(inode->i_mode))
		return;

	identity->sb_dev = inode->i_sb ? inode->i_sb->s_dev : 0;
	identity->ino = inode->i_ino;
	identity->size = i_size_read(inode);
	identity->mode = inode->i_mode;
	identity->valid = true;
}

static bool
xg_exec_identity_matches(const struct xg_trusted_exec_identity *identity,
			 struct file *file)
{
	struct inode *inode;

	if (!identity || !identity->valid || !file)
		return false;

	inode = file_inode(file);
	if (!inode)
		return false;

	return identity->sb_dev == (inode->i_sb ? inode->i_sb->s_dev : 0) &&
	       identity->ino == inode->i_ino &&
	       identity->size == i_size_read(inode) &&
	       identity->mode == inode->i_mode;
}

static struct xg_trusted_exec_identity *xg_trusted_shell_slot(const char *path)
{
	unsigned int i;

	if (!path)
		return NULL;

	for (i = 0; i < ARRAY_SIZE(xg_trusted_shells); i++) {
		if (xg_name_eq(path, xg_trusted_shells[i].path))
			return &xg_trusted_shells[i];
	}

	return NULL;
}

static bool xg_file_is_trusted_system_shell(struct file *file, const char *path,
					    const char *base)
{
	struct xg_trusted_exec_identity *identity;
	unsigned long flags;
	bool trusted;

	if (!xg_path_is_trusted_system_shell(path, base))
		return false;

	identity = xg_trusted_shell_slot(path);
	if (!identity)
		return false;

	spin_lock_irqsave(&xg_trusted_shell_lock, flags);
	if (!identity->valid) {
		xg_record_exec_identity(identity, file);
		trusted = identity->valid;
		if (trusted)
			pr_info(XG_TAG ": learned trusted shell %s dev=%u:%u "
				       "ino=%lu size=%lld\n",
				identity->path, MAJOR(identity->sb_dev),
				MINOR(identity->sb_dev), identity->ino,
				(long long)identity->size);
	} else {
		trusted = xg_exec_identity_matches(identity, file);
	}
	spin_unlock_irqrestore(&xg_trusted_shell_lock, flags);

	return trusted;
}

static bool xg_fdpath_is_empty_exec(const char *path)
{
	const char *p;

	if (!path || strncmp(path, "/dev/fd/", strlen("/dev/fd/")))
		return false;

	p = path + strlen("/dev/fd/");
	if (*p < '0' || *p > '9')
		return false;

	while (*p >= '0' && *p <= '9')
		p++;

	return *p == '\0';
}

static bool xg_elf_has_packed_layout(const u8 *buf, size_t len,
				     const Elf64_Ehdr *ehdr)
{
	const Elf64_Phdr *phdr;
	unsigned int i;
	bool executable_load = false;
	bool inflated_writable_load = false;
	size_t phdr_size;
	size_t phdr_end;

	if (ehdr->e_phentsize != sizeof(*phdr) || !ehdr->e_phnum)
		return false;

	if (ehdr->e_phoff > len)
		return false;

	if (check_mul_overflow((size_t)ehdr->e_phnum, sizeof(*phdr),
			       &phdr_size))
		return false;

	if (check_add_overflow((size_t)ehdr->e_phoff, phdr_size, &phdr_end) ||
	    phdr_end > len)
		return false;

	phdr = (const Elf64_Phdr *)(buf + ehdr->e_phoff);
	for (i = 0; i < ehdr->e_phnum; i++) {
		if (phdr[i].p_type != PT_LOAD)
			continue;

		if (phdr[i].p_flags & PF_X)
			executable_load = true;

		if ((phdr[i].p_flags & PF_W) &&
		    phdr[i].p_memsz > phdr[i].p_filesz &&
		    phdr[i].p_memsz - phdr[i].p_filesz > SZ_1M &&
		    phdr[i].p_memsz / 2 > phdr[i].p_filesz)
			inflated_writable_load = true;
	}

	return executable_load && inflated_writable_load;
}

static void xg_classify_exec_buf(const char *path, const u8 *buf, size_t len,
				 struct xg_exec_features *features)
{
	const Elf64_Ehdr *ehdr;

	memset(features, 0, sizeof(*features));

	if (!buf || !len)
		return;

	if (len >= SELFMAG && !memcmp(buf, ELFMAG, SELFMAG)) {
		features->elf = true;
		if (len < sizeof(*ehdr))
			return;

		ehdr = (const Elf64_Ehdr *)buf;
		features->elf_aarch64 = ehdr->e_machine == EM_AARCH64;
		features->elf_no_sections =
		    !ehdr->e_shoff || !ehdr->e_shnum || !ehdr->e_shentsize;
		features->elf_packed_layout =
		    features->elf_no_sections &&
		    xg_elf_has_packed_layout(buf, len, ehdr);
		return;
	}

	features->gzexe = xg_block_gzexe_exec &&
			  xg_mem_has(buf, len, "#gzexe") &&
			  xg_mem_has(buf, len, "skip=");

	if (!features->gzexe && path &&
	    xg_name_has_suffix(xg_basename(path), ".sh") &&
	    xg_mem_has(buf, len, "gzip"))
		features->gzexe = xg_mem_has(buf, len, "skip=");
}

static void xg_log_blocked_exec(const char *hook, const char *reason,
				const char *path,
				const struct xg_exec_features *features)
{
	unsigned int path_len =
	    path ? strnlen(path, XG_EXEC_PATH_LOG_BYTES) : 0;

	pr_warn_ratelimited(
	    XG_TAG
	    ": blocked exec via %s reason=%s pid=%d uid=%u comm=%s path=%.*s "
	    "elf=%u aarch64=%u no_sections=%u packed=%u gzexe=%u fd_empty=%u "
	    "trusted_shell=%u\n",
	    hook, reason, current->pid, __kuid_val(current_uid()),
	    current->comm, (int)path_len, path ? path : "",
	    features ? features->elf : 0, features ? features->elf_aarch64 : 0,
	    features ? features->elf_no_sections : 0,
	    features ? features->elf_packed_layout : 0,
	    features ? features->gzexe : 0,
	    features ? features->fd_empty_path : 0,
	    features ? features->trusted_system_shell : 0);
}

static void xg_log_blocked_shell_script(const char *hook, const char *reason,
					const char *script)
{
	unsigned int script_len =
	    script ? strnlen(script, XG_EXEC_PATH_LOG_BYTES) : 0;

	pr_warn_ratelimited(
	    XG_TAG ": blocked shell script via %s reason=%s pid=%d "
		   "uid=%u comm=%s script=%.*s\n",
	    hook, reason, current->pid, __kuid_val(current_uid()),
	    current->comm, (int)script_len, script ? script : "");
}

static unsigned int xg_gzexe_sample_seen_bits(const u8 *sample,
					      size_t sample_len)
{
	unsigned int seen = 0;

	if (!sample || !sample_len)
		return 0;

	if (xg_mem_has(sample, sample_len, "#gzexe") ||
	    xg_mem_has(sample, sample_len, "gzexe"))
		seen |= XG_GZEXE_SEEN_MAGIC;

	if (xg_mem_has(sample, sample_len, "skip=") ||
	    xg_mem_has(sample, sample_len, "skip ="))
		seen |= XG_GZEXE_SEEN_SKIP;

	if (xg_mem_has(sample, sample_len, "gzip") ||
	    xg_mem_has(sample, sample_len, "gunzip") ||
	    xg_mem_has(sample, sample_len, "zcat"))
		seen |= XG_GZEXE_SEEN_DECOMPRESS;

	if (xg_mem_has(sample, sample_len, "gztmp") ||
	    xg_mem_has(sample, sample_len, "$tmp") ||
	    xg_mem_has(sample, sample_len, "${tmp"))
		seen |= XG_GZEXE_SEEN_TMP;

	return seen;
}

static bool xg_gzexe_seen_blocks(unsigned int seen)
{
	if ((seen & XG_GZEXE_SEEN_MAGIC) && (seen & XG_GZEXE_SEEN_SKIP))
		return true;

	return (seen & XG_GZEXE_SEEN_SKIP) &&
	       (seen & XG_GZEXE_SEEN_DECOMPRESS) && (seen & XG_GZEXE_SEEN_TMP);
}

static const char *xg_shell_script_sample_block_reason(const char *path,
						       const u8 *sample,
						       size_t sample_len)
{
	struct xg_exec_features features;
	unsigned int seen;

	if (!sample || !sample_len)
		return NULL;

	xg_classify_exec_buf(path, sample, sample_len, &features);
	if (features.gzexe)
		return "gzexe self-extracting shell read";

	seen = xg_gzexe_sample_seen_bits(sample, sample_len);
	if (xg_gzexe_seen_blocks(seen))
		return "gzexe-like shell read";

	return NULL;
}

static void xg_mark_tagged_task(struct xg_tagged_task *tasks,
				unsigned int count, spinlock_t *lock,
				pid_t tgid, u32 sid)
{
	struct xg_tagged_task *slot = NULL;
	unsigned long flags;
	unsigned long now = jiffies;
	unsigned long max_age = msecs_to_jiffies(XG_GZEXE_CHAIN_WINDOW_MS);
	unsigned int i;

	if (tgid <= 0)
		return;

	spin_lock_irqsave(lock, flags);
	for (i = 0; i < count; i++) {
		if (tasks[i].tgid == tgid) {
			slot = &tasks[i];
			break;
		}

		if (!slot && (!tasks[i].tgid ||
			      time_after(now, tasks[i].last_seen + max_age)))
			slot = &tasks[i];
	}

	if (!slot)
		slot = &tasks[0];

	slot->tgid = tgid;
	slot->sid = sid;
	slot->last_seen = now;
	spin_unlock_irqrestore(lock, flags);
}

static bool xg_tagged_tgid(struct xg_tagged_task *tasks, unsigned int count,
			   spinlock_t *lock, pid_t tgid, u32 sid, bool expire)
{
	unsigned long flags;
	unsigned long now = jiffies;
	unsigned long max_age = msecs_to_jiffies(XG_GZEXE_CHAIN_WINDOW_MS);
	unsigned int i;
	bool tagged = false;

	if (tgid <= 0)
		return false;

	spin_lock_irqsave(lock, flags);
	for (i = 0; i < count; i++) {
		if (tasks[i].tgid != tgid)
			continue;
		if (expire && time_after(now, tasks[i].last_seen + max_age))
			continue;
		if (!tasks[i].sid || !sid || tasks[i].sid == sid) {
			tagged = true;
			break;
		}
	}
	spin_unlock_irqrestore(lock, flags);

	return tagged;
}

static bool xg_task_comm_is_shell_like(struct task_struct *task)
{
	if (!task)
		return false;

	return !strncmp(task->comm, "sh", TASK_COMM_LEN) ||
	       !strncmp(task->comm, "mksh", TASK_COMM_LEN) ||
	       !strncmp(task->comm, "ash", TASK_COMM_LEN) ||
	       !strncmp(task->comm, "bash", TASK_COMM_LEN) ||
	       !strncmp(task->comm, "dash", TASK_COMM_LEN) ||
	       !strncmp(task->comm, "zsh", TASK_COMM_LEN) ||
	       !strncmp(task->comm, "fish", 4);
}

static bool xg_mark_shell_polluted(struct task_struct *task, const char *reason)
{
	const struct cred *cred;
	u32 sid = 0;

	if (!xg_strict_shell_exec_guard || !xg_task_comm_is_shell_like(task))
		return false;

	cred = get_task_cred(task);
	if (cred) {
		sid = xg_cred_selinux_sid(cred);
		put_cred(cred);
	}

	xg_mark_tagged_task(xg_shell_polluted_tasks,
			    ARRAY_SIZE(xg_shell_polluted_tasks),
			    &xg_shell_polluted_lock, task->tgid, sid);

	pr_warn_ratelimited(
	    XG_TAG ": marked shell polluted via %s tracer_pid=%d tracer_uid=%u "
		   "tracer_comm=%s target_pid=%d target_tgid=%d target_comm=%s "
		   "sid=%u\n",
	    reason, current->pid, __kuid_val(current_uid()), current->comm,
	    task->pid, task->tgid, task->comm, sid);
	return true;
}

static bool xg_current_shell_polluted(const char **reason)
{
	const struct cred *cred = current_cred();
	struct task_struct *parent;
	pid_t parent_tgid = 0;
	u32 sid = xg_cred_selinux_sid(cred);

	if (!xg_strict_shell_exec_guard)
		return false;

	if (xg_tagged_tgid(
		xg_shell_polluted_tasks, ARRAY_SIZE(xg_shell_polluted_tasks),
		&xg_shell_polluted_lock, current->tgid, sid, false)) {
		if (reason)
			*reason = "polluted shell raw block-device write";
		return true;
	}

	rcu_read_lock();
	parent = rcu_dereference(current->real_parent);
	if (parent)
		parent_tgid = parent->tgid;
	rcu_read_unlock();

	if (xg_tagged_tgid(xg_shell_polluted_tasks,
			   ARRAY_SIZE(xg_shell_polluted_tasks),
			   &xg_shell_polluted_lock, parent_tgid, 0, false)) {
		if (reason)
			*reason =
			    "child of polluted shell raw block-device write";
		return true;
	}

	return false;
}

static void xg_mark_gzexe_tgid(pid_t tgid, u32 sid, const char *reason,
			       const char *path)
{
	unsigned int path_len =
	    path ? strnlen(path, XG_EXEC_PATH_LOG_BYTES) : 0;

	xg_mark_tagged_task(xg_gzexe_polluted_tasks,
			    ARRAY_SIZE(xg_gzexe_polluted_tasks),
			    &xg_gzexe_polluted_lock, tgid, sid);

	pr_warn_ratelimited(
	    XG_TAG ": marked gzexe execution chain via %s reader_pid=%d "
		   "reader_tgid=%d "
		   "uid=%u reader_comm=%s target_tgid=%d sid=%u path=%.*s\n",
	    reason, current->pid, current->tgid, __kuid_val(current_uid()),
	    current->comm, tgid, sid, (int)path_len, path ? path : "");
}

static void xg_mark_current_gzexe_chain(const char *reason, const char *path)
{
	const struct cred *cred = current_cred();
	struct task_struct *parent;
	pid_t parent_tgid = 0;
	u32 sid = xg_cred_selinux_sid(cred);

	xg_mark_gzexe_tgid(current->tgid, sid, reason, path);

	rcu_read_lock();
	parent = rcu_dereference(current->real_parent);
	if (parent)
		parent_tgid = parent->tgid;
	rcu_read_unlock();

	if (parent_tgid && parent_tgid != current->tgid)
		xg_mark_gzexe_tgid(parent_tgid, sid, reason, path);
}

static bool xg_current_gzexe_polluted(const char **reason)
{
	const struct cred *cred = current_cred();
	struct task_struct *parent;
	pid_t parent_tgid = 0;
	u32 sid = xg_cred_selinux_sid(cred);

	if (!xg_block_gzexe_exec)
		return false;

	if (xg_tagged_tgid(xg_gzexe_polluted_tasks,
			   ARRAY_SIZE(xg_gzexe_polluted_tasks),
			   &xg_gzexe_polluted_lock, current->tgid, sid, true)) {
		if (reason)
			*reason = "gzexe-read polluted execution chain";
		return true;
	}

	rcu_read_lock();
	parent = rcu_dereference(current->real_parent);
	if (parent)
		parent_tgid = parent->tgid;
	rcu_read_unlock();

	if (xg_tagged_tgid(xg_gzexe_polluted_tasks,
			   ARRAY_SIZE(xg_gzexe_polluted_tasks),
			   &xg_gzexe_polluted_lock, parent_tgid, 0, true)) {
		if (reason)
			*reason =
			    "child of gzexe-read polluted execution chain";
		return true;
	}

	return false;
}

static bool xg_current_dynamic_code_polluted(const char **reason)
{
	const struct cred *cred = current_cred();
	u32 sid = xg_cred_selinux_sid(cred);

	if (xg_tagged_tgid(xg_dynamic_tasks, ARRAY_SIZE(xg_dynamic_tasks),
			   &xg_dynamic_lock, current->tgid, sid, false)) {
		if (reason)
			*reason =
			    "dynamic-code polluted raw block-device write";
		return true;
	}

	return false;
}

static bool xg_mark_current_dynamic_code(const char *reason, unsigned long addr,
					 unsigned long len)
{
	const struct cred *cred = current_cred();
	unsigned int uid = __kuid_val(cred->uid);
	u32 sid = xg_cred_selinux_sid(cred);
	bool block = xg_block_privileged_dynamic_code &&
		     xg_current_is_privileged_dynamic_subject();

	xg_mark_tagged_task(xg_dynamic_tasks, ARRAY_SIZE(xg_dynamic_tasks),
			    &xg_dynamic_lock, current->tgid, sid);

	pr_warn_ratelimited(
	    XG_TAG ": %s suspicious dynamic code via %s pid=%d tgid=%d "
		   "uid=%u comm=%s sid=%u addr=0x%lx len=%lu\n",
	    block ? "blocked" : "marked", reason, current->pid, current->tgid,
	    uid, current->comm, sid, addr, len);

	return block;
}

static bool xg_task_has_radio_storage_identity(struct task_struct *task,
					       const struct cred *cred)
{
	if (!task || !cred)
		return false;

	if (__kuid_val(cred->uid) != xg_nv_writer_uid)
		return false;

	return !strncmp(task->comm, XG_NV_WRITER_COMM, TASK_COMM_LEN);
}

static bool xg_task_is_radio_storage(struct task_struct *task, u32 *sidp)
{
	const struct cred *cred;
	u32 sid;
	u32 trusted;
	bool match = false;

	if (!task)
		return false;

	cred = get_task_cred(task);
	if (!cred)
		return false;

	sid = xg_cred_selinux_sid(cred);
	trusted = READ_ONCE(xg_nv_writer_sid);

	if (trusted && sid == trusted) {
		match = true;
	} else if (sid && xg_task_has_radio_storage_identity(task, cred)) {
		WRITE_ONCE(xg_nv_writer_sid, sid);
		match = true;
	}

	put_cred(cred);

	if (match && sidp)
		*sidp = sid;

	return match;
}

static bool xg_mark_radio_storage_polluted(struct task_struct *task,
					   const char *reason)
{
	u32 sid = 0;

	if (!xg_block_polluted_radio_nv ||
	    !xg_task_is_radio_storage(task, &sid))
		return false;

	xg_mark_tagged_task(xg_polluted_tasks, ARRAY_SIZE(xg_polluted_tasks),
			    &xg_polluted_lock, task->tgid, sid);

	pr_warn_ratelimited(XG_TAG ": marked radio NV writer polluted via %s "
				   "tracer_pid=%d tracer_uid=%u "
				   "tracer_comm=%s target_pid=%d "
				   "target_tgid=%d target_comm=%s sid=%u\n",
			    reason, current->pid, __kuid_val(current_uid()),
			    current->comm, task->pid, task->tgid, task->comm,
			    sid);
	return true;
}

static bool xg_current_radio_storage_polluted(const char **reason)
{
	const struct cred *cred = current_cred();
	u32 sid = xg_cred_selinux_sid(cred);

	if (!xg_block_polluted_radio_nv)
		return false;

	if (READ_ONCE(current->ptrace) & PT_PTRACED) {
		xg_mark_radio_storage_polluted(current, "active ptrace");
		if (reason)
			*reason = "ptraced protected radio NV writer";
		return true;
	}

	if (xg_tagged_tgid(xg_polluted_tasks, ARRAY_SIZE(xg_polluted_tasks),
			   &xg_polluted_lock, current->tgid, sid, false)) {
		if (reason)
			*reason = "polluted protected radio NV writer";
		return true;
	}

	return false;
}

static bool xg_current_is_trusted_radio_storage(void)
{
	const struct cred *cred = current_cred();
	u32 sid = xg_cred_selinux_sid(cred);
	u32 trusted = READ_ONCE(xg_nv_writer_sid);

	if (trusted && sid == trusted)
		return true;

	if (trusted || !sid)
		return false;

	if (!xg_task_has_radio_storage_identity(current, cred))
		return false;

	WRITE_ONCE(xg_nv_writer_sid, sid);
	pr_warn_ratelimited(
	    XG_TAG
	    ": lazily learned radio NV writer sid=%u from uid=%u comm=%s\n",
	    sid, __kuid_val(cred->uid), current->comm);
	return true;
}

static struct partition_meta_info *xg_bdev_meta_info(struct block_device *bdev)
{
	if (!bdev)
		return NULL;

#if LINUX_VERSION_CODE < KERNEL_VERSION(5, 11, 0)
	if (!bdev->bd_part)
		return NULL;

	return bdev->bd_part->info;
#else
	return bdev->bd_meta_info;
#endif
}

static const u8 *xg_bdev_part_name(struct block_device *bdev, unsigned int *len)
{
	struct partition_meta_info *info;
	const u8 *name;
	unsigned int i;

	if (len)
		*len = 0;

	info = xg_bdev_meta_info(bdev);
	if (!info)
		return NULL;

	name = info->volname;
	if (!name[0])
		return NULL;

	for (i = 0; i < PARTITION_META_INFO_VOLNAMELTH && name[i]; i++) {
	}

	if (!i)
		return NULL;

	if (len)
		*len = i;
	return name;
}

static bool xg_part_name_eq(const u8 *name, unsigned int len, const char *base)
{
	size_t base_len = strlen(base);

	if (len == base_len && !memcmp(name, base, base_len))
		return true;

	if (len == base_len + 2 && !memcmp(name, base, base_len) &&
	    name[base_len] == '_' &&
	    (name[base_len + 1] == 'a' || name[base_len + 1] == 'b'))
		return true;

	return false;
}

static bool xg_part_in_list(const u8 *name, unsigned int len,
			    const char *const *list, unsigned int count)
{
	unsigned int i;

	if (!name || !len)
		return false;

	for (i = 0; i < count; i++) {
		if (xg_part_name_eq(name, len, list[i]))
			return true;
	}

	return false;
}

static enum xg_part_class xg_classify_bdev(struct block_device *bdev)
{
	const u8 *name;
	unsigned int len;

	name = xg_bdev_part_name(bdev, &len);
	if (!name)
		return XG_PART_GENERIC;

	if (xg_part_in_list(name, len, xg_firmware_partitions,
			    ARRAY_SIZE(xg_firmware_partitions)))
		return XG_PART_FIRMWARE;

	if (xg_part_in_list(name, len, xg_radio_nv_partitions,
			    ARRAY_SIZE(xg_radio_nv_partitions)))
		return XG_PART_RADIO_NV;

	return XG_PART_GENERIC;
}

static struct block_device *xg_file_bdev(struct file *file)
{
	struct inode *inode;

	if (!file)
		return NULL;

	inode = file_inode(file);
	if (!inode || !S_ISBLK(inode->i_mode))
		return NULL;

	return I_BDEV(inode);
}

static bool xg_bdev_is_zram(struct block_device *bdev)
{
	const char *name;

	if (!bdev || !bdev->bd_disk)
		return false;

	name = bdev->bd_disk->disk_name;
	return name && !strncmp(name, "zram", 4);
}

static dev_t xg_bdev_dev(struct file *file, struct block_device *bdev)
{
	struct inode *inode;

	if (bdev)
		return bdev->bd_dev;

	if (!file)
		return 0;

	inode = file_inode(file);
	return inode ? inode->i_rdev : 0;
}

static void xg_log_blocked_access(const char *hook, const char *op,
				  struct file *file, struct block_device *bdev,
				  unsigned int cmd)
{
	const u8 *part;
	unsigned int part_len = 0;
	dev_t dev;

	if (!bdev)
		bdev = xg_file_bdev(file);

	dev = xg_bdev_dev(file, bdev);
	part = xg_bdev_part_name(bdev, &part_len);

	if (part_len) {
		pr_warn_ratelimited(
		    XG_TAG ": blocked %s via %s pid=%d uid=%u comm=%s "
			   "dev=%u:%u part=%.*s cmd=0x%x\n",
		    op, hook, current->pid, __kuid_val(current_uid()),
		    current->comm, MAJOR(dev), MINOR(dev), part_len, part, cmd);
	} else {
		pr_warn_ratelimited(XG_TAG ": blocked %s via %s pid=%d uid=%u "
					   "comm=%s dev=%u:%u cmd=0x%x\n",
				    op, hook, current->pid,
				    __kuid_val(current_uid()), current->comm,
				    MAJOR(dev), MINOR(dev), cmd);
	}
}

static bool xg_is_destructive_blk_ioctl(unsigned int cmd)
{
	switch (cmd) {
	case BLKROSET:
	case BLKRRPART:
	case BLKPG:
	case BLKRASET:
	case BLKFRASET:
	case BLKSECTSET:
	case BLKBSZSET:
	case BLKDISCARD:
	case BLKSECDISCARD:
	case BLKZEROOUT:
#ifdef BLKRESETZONE
	case BLKRESETZONE:
#endif
#ifdef BLKOPENZONE
	case BLKOPENZONE:
#endif
#ifdef BLKCLOSEZONE
	case BLKCLOSEZONE:
#endif
#ifdef BLKFINISHZONE
	case BLKFINISHZONE:
#endif
		return true;
	default:
		return false;
	}
}

static bool xg_raw_block_should_block(struct file *file, bool destructive_ioctl,
				      const char **reason)
{
	struct block_device *bdev = xg_file_bdev(file);
	enum xg_part_class part_class;

	if (!bdev)
		return false;

	if (xg_current_dynamic_code_polluted(reason))
		return true;

	if (xg_current_gzexe_polluted(reason))
		return true;

	if (xg_current_shell_polluted(reason))
		return true;

	part_class = xg_classify_bdev(bdev);
	if (part_class == XG_PART_FIRMWARE) {
		*reason = destructive_ioctl
			      ? "protected firmware partition ioctl"
			      : "protected firmware partition raw write";
		return true;
	}

	if (part_class == XG_PART_RADIO_NV) {
		if (xg_block_untrusted_radio_nv &&
		    !xg_current_is_trusted_radio_storage()) {
			*reason =
			    destructive_ioctl
				? "untrusted protected radio NV ioctl"
				: "untrusted protected radio NV raw write";
			return true;
		}

		if (xg_current_radio_storage_polluted(reason))
			return true;

		return false;
	}

	if (destructive_ioctl && !xg_bdev_is_zram(bdev)) {
		*reason = "destructive raw block-device ioctl";
		return true;
	}

	return false;
}

static bool xg_is_selinux_enforce_file(struct file *file)
{
	char path_buf[XG_EXEC_PATH_LOG_BYTES];
	char *path;

	path = xg_file_path(file, path_buf, sizeof(path_buf));
	return path && xg_name_eq(path, "/sys/fs/selinux/enforce");
}

static const char *xg_exec_block_reason(const char *path,
					const struct xg_exec_features *features)
{
	const char *base = xg_basename(path);
	bool shell_name = xg_basename_is_shell_like(base);
	bool shell_task = xg_current_is_shell_like();
	bool tmp_path = xg_path_has_tmp_marker(path);
	bool system_exec_path = xg_path_is_system_exec_path(path);
	bool suspicious_name =
	    !system_exec_path && xg_basename_is_randomish(base);

	if (!xg_exec_guard_applies())
		return NULL;

	if (features->fd_empty_path)
		return "execveat empty-path fd execution";

	if (xg_current_gzexe_polluted(NULL) && features->elf &&
	    !features->trusted_system_shell &&
	    (tmp_path || features->elf_no_sections ||
	     features->elf_packed_layout))
		return "gzexe chain executed temporary ELF";

	if (xg_current_shell_polluted(NULL) &&
	    (features->elf || features->gzexe) &&
	    !features->trusted_system_shell)
		return "polluted shell executed suspicious payload";

	if (xg_block_disguised_shell_exec && features->elf &&
	    xg_name_has_suffix(base, ".sh"))
		return "ELF disguised as .sh";

	if (xg_block_disguised_shell_exec && features->elf && shell_name &&
	    xg_path_is_trusted_system_shell(path, base) &&
	    !features->trusted_system_shell)
		return "untrusted system shell identity";

	if (xg_block_disguised_shell_exec && features->elf && shell_name &&
	    (features->elf_no_sections || features->elf_packed_layout))
		return "suspicious ELF shell payload";

	if (features->gzexe)
		return "gzexe self-extracting shell";

	if (xg_strict_shell_exec_guard && features->elf && shell_task &&
	    (tmp_path || suspicious_name))
		return "shell executed temporary ELF";

	if (xg_block_packed_elf_exec && features->elf_packed_layout &&
	    (tmp_path || suspicious_name))
		return "packed temporary ELF";

	if (xg_strict_shell_exec_guard && features->elf_no_sections &&
	    shell_task && (tmp_path || suspicious_name))
		return "shell executed no-section ELF";

	return NULL;
}

static int xg_bprm_check_security(struct linux_binprm *bprm)
{
	struct xg_exec_features features;
	const char *path;
	const char *reason;

	if (!bprm)
		return 0;

	path = bprm->filename ? bprm->filename : bprm->interp;
	xg_classify_exec_buf(path, bprm->buf, BINPRM_BUF_SIZE, &features);
	features.fd_empty_path = xg_fdpath_is_empty_exec(bprm->fdpath);
	features.trusted_system_shell = xg_file_is_trusted_system_shell(
	    bprm->file, path, xg_basename(path));
	reason = xg_exec_block_reason(path, &features);
	if (!reason)
		return 0;

	xg_log_blocked_exec("bprm_check_security", reason, path, &features);
	return -EPERM;
}

static bool xg_file_should_sample_script(struct file *file, const char *path)
{
	struct inode *inode;

	if (!xg_block_shell_writable_scripts || !xg_exec_guard_applies() ||
	    !xg_current_is_script_reader_like())
		return false;

	if (!file || !path)
		return false;

	inode = file_inode(file);
	if (!inode || !S_ISREG(inode->i_mode))
		return false;

	return i_size_read(inode) > 0;
}

static int xg_sample_opened_script(struct file *file, const char *path)
{
	u8 *sample;
	loff_t pos = 0;
	ssize_t nread;
	const char *reason;
	int ret = 0;

	if (!xg_file_should_sample_script(file, path))
		return 0;

	sample = kmalloc(XG_EXEC_SAMPLE_BYTES, GFP_KERNEL);
	if (!sample)
		return 0;

	nread = kernel_read(file, sample, XG_EXEC_SAMPLE_BYTES, &pos);
	if (nread <= 0)
		goto out;

	reason = xg_shell_script_sample_block_reason(path, sample, nread);
	if (!reason)
		goto out;

	xg_mark_current_gzexe_chain(reason, path);
	xg_log_blocked_shell_script("file_open", reason, path);
	ret = -EACCES;

out:
	kfree(sample);
	return ret;
}

static bool xg_gzexe_temp_actor_should_block(const char *path,
					     const char **reason)
{
	bool polluted;
	bool suspicious_actor;

	if (!path || !xg_path_has_gzexe_tmp_marker(path))
		return false;

	polluted = xg_current_gzexe_polluted(reason);
	suspicious_actor = xg_current_is_script_reader_like() ||
			   xg_current_is_privileged_dynamic_subject();
	if (!polluted && !suspicious_actor)
		return false;

	if (reason && !*reason)
		*reason = "gzexe temp extraction artifact";

	return true;
}

static int xg_file_open(struct file *file)
{
	char path_buf[XG_EXEC_PATH_LOG_BYTES];
	char *path;
	const char *reason = NULL;
	const char *raw_reason = NULL;
	int ret;

	if (!file)
		return 0;

	path = xg_file_path(file, path_buf, sizeof(path_buf));

	if ((file->f_mode & FMODE_WRITE) && xg_is_selinux_enforce_file(file)) {
		pr_warn_ratelimited(
		    XG_TAG ": blocked SELinux enforce write via file_open "
			   "pid=%d uid=%u comm=%s\n",
		    current->pid, __kuid_val(current_uid()), current->comm);
		return -EPERM;
	}

	if ((file->f_mode & FMODE_WRITE) &&
	    xg_raw_block_should_block(file, false, &raw_reason)) {
		xg_log_blocked_access("file_open", raw_reason, file, NULL, 0);
		return -EPERM;
	}

	if ((file->f_mode & FMODE_WRITE) &&
	    xg_gzexe_temp_actor_should_block(path, &reason)) {
		xg_mark_current_gzexe_chain(reason, path);
		xg_log_blocked_shell_script("file_open", reason, path);
		return -EACCES;
	}

	ret = xg_sample_opened_script(file, path);
	if (ret)
		return ret;

	return 0;
}

static int xg_file_permission(struct file *file, int mask)
{
	const char *reason = NULL;

	if (!(mask & MAY_WRITE))
		return 0;

	if (xg_is_selinux_enforce_file(file)) {
		pr_warn_ratelimited(
		    XG_TAG ": blocked SELinux enforce write via "
			   "file_permission pid=%d uid=%u comm=%s\n",
		    current->pid, __kuid_val(current_uid()), current->comm);
		return -EPERM;
	}

	if (!xg_raw_block_should_block(file, false, &reason))
		return 0;

	xg_log_blocked_access("file_permission", reason, file, NULL, 0);
	return -EPERM;
}

static int xg_file_ioctl(struct file *file, unsigned int cmd, unsigned long arg)
{
	const char *reason = NULL;

	if (!file || !xg_is_destructive_blk_ioctl(cmd))
		return 0;

	if (!xg_raw_block_should_block(file, true, &reason))
		return 0;

	xg_log_blocked_access("file_ioctl", reason, file, NULL, cmd);
	return -EPERM;
}

static int xg_mmap_file(struct file *file, unsigned long reqprot,
			unsigned long prot, unsigned long flags)
{
	bool suspicious = false;
	const char *reason = NULL;

	if (!(prot & PROT_EXEC))
		return 0;

	if ((reqprot & PROT_WRITE) && (reqprot & PROT_EXEC)) {
		suspicious = true;
		reason = "writable executable mmap";
	} else if (!file || (flags & MAP_ANONYMOUS)) {
		suspicious = true;
		reason = "anonymous executable mmap";
	}

	if (!suspicious)
		return 0;

	if (!xg_mark_current_dynamic_code(reason, 0, 0))
		return 0;

	return -EPERM;
}

static int xg_file_mprotect(struct vm_area_struct *vma, unsigned long reqprot,
			    unsigned long prot)
{
	unsigned long oldflags;
	unsigned long len;
	bool suspicious = false;
	const char *reason = NULL;

	if (!vma || !(prot & PROT_EXEC))
		return 0;

	oldflags = READ_ONCE(vma->vm_flags);
	if (oldflags & VM_WRITE) {
		suspicious = true;
		reason = "writable mapping made executable";
	} else if (!vma->vm_file) {
		suspicious = true;
		reason = "anonymous mapping made executable";
	}

	if (!suspicious)
		return 0;

	len = vma->vm_end > vma->vm_start ? vma->vm_end - vma->vm_start : 0;
	if (!xg_mark_current_dynamic_code(reason, vma->vm_start, len))
		return 0;

	return -EPERM;
}

static int xg_ptrace_access_check(struct task_struct *child, unsigned int mode)
{
	bool blocked = false;

	if (!(mode & PTRACE_MODE_ATTACH))
		return 0;

	if (child)
		blocked =
		    xg_mark_shell_polluted(child, "ptrace_access_check") ||
		    xg_mark_radio_storage_polluted(child,
						   "ptrace_access_check");

	if (!blocked)
		return 0;

	pr_warn_ratelimited(
	    XG_TAG ": blocked ptrace attach against protected task pid=%d "
		   "uid=%u comm=%s target_pid=%d target_comm=%s\n",
	    current->pid, __kuid_val(current_uid()), current->comm,
	    child ? child->pid : 0, child ? child->comm : "");
	return -EPERM;
}

static int xg_ptrace_traceme(struct task_struct *parent)
{
	bool blocked = false;

	blocked = xg_mark_shell_polluted(current, "ptrace_traceme") ||
		  xg_mark_radio_storage_polluted(current, "ptrace_traceme");
	if (!blocked)
		return 0;

	pr_warn_ratelimited(
	    XG_TAG ": blocked ptrace traceme pid=%d uid=%u comm=%s "
		   "parent_pid=%d parent_comm=%s\n",
	    current->pid, __kuid_val(current_uid()), current->comm,
	    parent ? parent->pid : 0, parent ? parent->comm : "");
	return -EPERM;
}

static void xg_watchdog_report(const char *area, const char *name,
			       const char *detail)
{
	unsigned long flags;

	spin_lock_irqsave(&xg_watchdog_lock, flags);
	xg_watchdog_tripped = true;
	spin_unlock_irqrestore(&xg_watchdog_lock, flags);

	pr_err_ratelimited(XG_TAG ": watchdog detected %s corruption target=%s "
				  "detail=%s pid=%d uid=%u comm=%s\n",
			   area, name, detail, current->pid,
			   __kuid_val(current_uid()), current->comm);
}

static void xg_watchdog_restore_bool(bool *value, const char *name)
{
	if (READ_ONCE(*value))
		return;

	xg_watchdog_report("guard parameter", name, "disabled");
	WRITE_ONCE(*value, true);
	pr_warn_ratelimited(XG_TAG ": watchdog repaired guard parameter "
				   "target=%s detail=restored true\n",
			    name);
}

static void xg_watchdog_workfn(struct work_struct *work)
{
	if (READ_ONCE(xg_watchdog_canary_a) != XG_WATCHDOG_CANARY_A) {
		xg_watchdog_report("canary", "canary_a", "mismatch");
		WRITE_ONCE(xg_watchdog_canary_a, XG_WATCHDOG_CANARY_A);
	}

	if (READ_ONCE(xg_watchdog_canary_b) != XG_WATCHDOG_CANARY_B) {
		xg_watchdog_report("canary", "canary_b", "mismatch");
		WRITE_ONCE(xg_watchdog_canary_b, XG_WATCHDOG_CANARY_B);
	}

	xg_watchdog_restore_bool(&xg_block_setenforce, "block_setenforce");
	xg_watchdog_restore_bool(&xg_block_untrusted_radio_nv,
				 "block_untrusted_radio_nv");
	xg_watchdog_restore_bool(&xg_block_polluted_radio_nv,
				 "block_polluted_radio_nv");
	xg_watchdog_restore_bool(&xg_block_privileged_dynamic_code,
				 "block_privileged_dynamic_code");
	xg_watchdog_restore_bool(&xg_exec_guard_all_uid, "exec_guard_all_uid");
	xg_watchdog_restore_bool(&xg_strict_shell_exec_guard,
				 "strict_shell_exec_guard");
	xg_watchdog_restore_bool(&xg_block_disguised_shell_exec,
				 "block_disguised_shell_exec");
	xg_watchdog_restore_bool(&xg_block_gzexe_exec, "block_gzexe_exec");
	xg_watchdog_restore_bool(&xg_block_packed_elf_exec,
				 "block_packed_elf_exec");
	xg_watchdog_restore_bool(&xg_block_shell_writable_scripts,
				 "block_shell_writable_scripts");

	queue_delayed_work(system_wq, &xg_watchdog_work,
			   msecs_to_jiffies(XG_WATCHDOG_INTERVAL_MS));
}

static int __init xg_watchdog_start(void)
{
	INIT_DELAYED_WORK(&xg_watchdog_work, xg_watchdog_workfn);
	queue_delayed_work(system_wq, &xg_watchdog_work,
			   msecs_to_jiffies(XG_WATCHDOG_INTERVAL_MS));
	pr_info(XG_TAG ": watchdog started\n");
	return 0;
}
late_initcall(xg_watchdog_start);

static struct security_hook_list xg_hooks[] __lsm_ro_after_init = {
    LSM_HOOK_INIT(bprm_check_security, xg_bprm_check_security),
    LSM_HOOK_INIT(file_open, xg_file_open),
    LSM_HOOK_INIT(file_permission, xg_file_permission),
    LSM_HOOK_INIT(file_ioctl, xg_file_ioctl),
    LSM_HOOK_INIT(mmap_file, xg_mmap_file),
    LSM_HOOK_INIT(file_mprotect, xg_file_mprotect),
    LSM_HOOK_INIT(ptrace_access_check, xg_ptrace_access_check),
    LSM_HOOK_INIT(ptrace_traceme, xg_ptrace_traceme),
};

static int __init xg_ddk_init(void)
{
	xg_ddk_add_hooks(xg_hooks, ARRAY_SIZE(xg_hooks));
	pr_info(XG_TAG ": LSM enabled\n");
	return 0;
}

#ifdef XG_DDK_USE_DEFINE_LSM
DEFINE_LSM(xingguang_ddk) = {
    .name = "xingguang_ddk",
    .init = xg_ddk_init,
};
#else
security_initcall(xg_ddk_init);
#endif

MODULE_LICENSE("GPL");
MODULE_DESCRIPTION("Xingguang DDK destructive action guard LSM");
