// Command diskati is the Windows companion for the Diskati Android app.
// Phase 3: reads real disk capacity and manages its own background lifecycle.
// No phone connection, scanning or deletion exists yet.
package main

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"os"
	"os/exec"
	"os/signal"
	"path/filepath"
	"runtime"
	"strconv"
	"time"

	"diskati/companion/internal/control"
	"diskati/companion/internal/devices"
	"diskati/companion/internal/disks"
	"diskati/companion/internal/logx"
	"diskati/companion/internal/pairing"
	"diskati/companion/internal/pki"
	"diskati/companion/internal/remote"
)

const defaultPort = 47831

var version = "0.4.0" // overridden with -ldflags "-X main.version=..."

const runKeyName = "Diskati"

const usage = `ديسكاتي — برنامج Windows المرافق (الإصدار %s)

الاستخدام:
  diskati start       تشغيل البرنامج في الخلفية
  diskati run         تشغيل في هذه النافذة (للاختبار)
  diskati status      عرض حالة البرنامج
  diskati stop        إيقاف البرنامج
  diskati disks       عرض الأقراص ومساحاتها (JSON)
  diskati pair        إنشاء رمز اقتران لربط هاتف جديد
  diskati devices     عرض الهواتف المرتبطة
  diskati unpair ID   إلغاء ربط هاتف (أو: diskati unpair all)
  diskati reset-cert  إعادة إنشاء شهادة الأمان (يلغي ربط كل الهواتف)
  diskati install     التشغيل مع تسجيل دخول المستخدم (بدون صلاحيات مدير)
  diskati uninstall   إلغاء التشغيل التلقائي
  diskati version     رقم الإصدار
`

func main() {
	if len(os.Args) < 2 {
		fmt.Printf(usage, version)
		os.Exit(2)
	}
	dir, err := dataDir()
	if err != nil {
		fatal(err)
	}
	switch os.Args[1] {
	case "run":
		fatalIf(cmdRun(dir))
	case "start":
		fatalIf(cmdStart(dir))
	case "status":
		fatalIf(cmdStatus(dir))
	case "stop":
		fatalIf(cmdStop(dir))
	case "disks":
		fatalIf(cmdDisks())
	case "pair":
		fatalIf(cmdPair(dir))
	case "devices":
		fatalIf(cmdDevices(dir))
	case "unpair":
		if len(os.Args) < 3 {
			fatal(errors.New("حدّد المعرّف أو all"))
		}
		fatalIf(cmdUnpair(dir, os.Args[2]))
	case "reset-cert":
		fatalIf(cmdResetCert(dir))
	case "install":
		fatalIf(cmdInstall())
	case "uninstall":
		fatalIf(cmdUninstall())
	case "version":
		fmt.Println(version)
	default:
		fmt.Printf(usage, version)
		os.Exit(2)
	}
}

// dataDir holds the control file and the technical log (never file names or contents).
func dataDir() (string, error) {
	if d := os.Getenv("DISKATI_DATA_DIR"); d != "" {
		return d, nil
	}
	if runtime.GOOS == "windows" {
		if base := os.Getenv("LOCALAPPDATA"); base != "" {
			return filepath.Join(base, "Diskati"), nil
		}
	}
	base, err := os.UserConfigDir()
	if err != nil {
		return "", err
	}
	return filepath.Join(base, "diskati"), nil
}

func cmdRun(dir string) error {
	if _, err := control.GetStatus(dir); err == nil {
		return errors.New("البرنامج يعمل بالفعل")
	}
	lg, f, err := logx.New(dir)
	if err != nil {
		return err
	}
	defer f.Close()

	cert, fp, err := pki.LoadOrCreate(dir)
	if err != nil {
		return fmt.Errorf("تعذّر تجهيز شهادة الأمان: %w", err)
	}
	store, err := devices.Open(dir)
	if err != nil {
		return fmt.Errorf("تعذّر فتح سجل الأجهزة: %w", err)
	}
	pm := pairing.NewManager()
	host, _ := os.Hostname()
	provider := disks.SystemProvider{}

	rs := remote.New(remote.Options{Port: portFromEnv(), ComputerName: host, Version: version},
		cert, fp, provider, store, pm, lg)
	if err := rs.Start(); err != nil {
		lg.Printf("cannot start phone endpoint: %v", err)
		return fmt.Errorf("تعذّر فتح منفذ الاتصال بالهاتف (هل المنفذ مستخدم؟): %w", err)
	}

	admin := &app{pair: pm, store: store, port: rs.Port, fingerprint: fp}
	srv := control.New(dir, version, provider, admin, lg)
	if err := srv.Start(); err != nil {
		_ = rs.Shutdown(context.Background())
		return err
	}
	lg.Printf("diskati %s started", version)
	fmt.Println("ديسكاتي يعمل. للإيقاف: diskati stop")

	ctx, cancel := signal.NotifyContext(context.Background(), os.Interrupt)
	defer cancel()
	select {
	case <-ctx.Done():
		lg.Printf("stopping: interrupt")
	case <-srv.Stopped():
		lg.Printf("stopping: stop requested")
	}
	sctx, scancel := context.WithTimeout(context.Background(), 3*time.Second)
	defer scancel()
	_ = rs.Shutdown(sctx)
	err = srv.Shutdown(sctx)
	lg.Printf("stopped")
	return err
}

func portFromEnv() int {
	if v := os.Getenv("DISKATI_PORT"); v != "" {
		if n, err := strconv.Atoi(v); err == nil && n >= 0 && n < 65536 {
			return n
		}
	}
	return defaultPort
}

// cmdStart launches `run` as a detached background process and waits until it answers.
func cmdStart(dir string) error {
	if st, err := control.GetStatus(dir); err == nil {
		fmt.Printf("يعمل بالفعل (PID %d)\n", st.PID)
		return nil
	}
	exe, err := os.Executable()
	if err != nil {
		return err
	}
	cmd := exec.Command(exe, "run")
	cmd.Env = os.Environ()
	detach(cmd)
	if err := cmd.Start(); err != nil {
		return err
	}
	_ = cmd.Process.Release()
	for i := 0; i < 30; i++ {
		time.Sleep(200 * time.Millisecond)
		if st, err := control.GetStatus(dir); err == nil {
			fmt.Printf("بدأ التشغيل في الخلفية (PID %d)\n", st.PID)
			return nil
		}
	}
	return errors.New("لم يستجب البرنامج بعد التشغيل. راجع diskati.log في مجلد البيانات")
}

func cmdStatus(dir string) error {
	st, err := control.GetStatus(dir)
	if errors.Is(err, control.ErrNotRunning) {
		fmt.Println("غير مشغّل")
		os.Exit(1)
	}
	if err != nil {
		return err
	}
	fmt.Printf("يعمل • الإصدار %s • PID %d • منذ %d ثانية\n", st.Version, st.PID, st.UptimeSeconds)
	return nil
}

func cmdStop(dir string) error {
	err := control.RequestStop(dir)
	if errors.Is(err, control.ErrNotRunning) {
		fmt.Println("غير مشغّل")
		return nil
	}
	if err != nil {
		return err
	}
	for i := 0; i < 25; i++ {
		time.Sleep(200 * time.Millisecond)
		if _, e := control.GetStatus(dir); errors.Is(e, control.ErrNotRunning) {
			fmt.Println("تم الإيقاف")
			return nil
		}
	}
	return errors.New("لم يتوقف البرنامج خلال المهلة")
}

func cmdDisks() error {
	d, err := disks.SystemProvider{}.List()
	if err != nil {
		return err
	}
	enc := json.NewEncoder(os.Stdout)
	enc.SetIndent("", "  ")
	return enc.Encode(d)
}

func cmdPair(dir string) error {
	if _, err := control.GetStatus(dir); errors.Is(err, control.ErrNotRunning) {
		if err := cmdStart(dir); err != nil {
			return err
		}
	} else if err != nil {
		return err
	}
	before, err := control.ListDevices(dir)
	if err != nil {
		return err
	}
	known := map[string]bool{}
	for _, d := range before {
		known[d.ID] = true
	}
	info, err := control.StartPairing(dir)
	if err != nil {
		return err
	}
	fmt.Println()
	fmt.Println("رمز الاقتران:", info.Code)
	fmt.Printf("صالح حتى %s (5 دقائق) ويُستخدم مرة واحدة فقط.\n", info.ExpiresAt.Local().Format("15:04:05"))
	fmt.Println()
	fmt.Println("في تطبيق ديسكاتي على الهاتف أدخل الرمز مع أحد هذه العناوين:")
	if len(info.Addresses) == 0 {
		fmt.Println("  (لم يُعثر على عنوان شبكة خاصة. تأكد أن الكمبيوتر متصل بالشبكة نفسها)")
	}
	for _, a := range info.Addresses {
		fmt.Printf("  %s   المنفذ: %d\n", a, info.Port)
	}
	fmt.Println()
	fmt.Println("إذا ظهر تنبيه من جدار حماية Windows عند أول مرة، اسمح بالوصول على الشبكات الخاصة فقط.")
	fmt.Println("بصمة الشهادة:", info.Fingerprint)
	fmt.Println("بانتظار الهاتف… (Ctrl+C للإلغاء)")

	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt)
	defer stop()
	tick := time.NewTicker(time.Second)
	defer tick.Stop()
	for {
		select {
		case <-ctx.Done():
			_ = control.CancelPairing(dir)
			fmt.Println("أُلغي الاقتران")
			return nil
		case <-tick.C:
			now, err := control.ListDevices(dir)
			if err != nil {
				return err
			}
			for _, d := range now {
				if !known[d.ID] {
					fmt.Printf("تم ربط الجهاز: %s (المعرّف %s)\n", d.Name, d.ID)
					return nil
				}
			}
			if time.Now().After(info.ExpiresAt) {
				_ = control.CancelPairing(dir)
				fmt.Println("انتهت صلاحية الرمز دون اقتران. أعد تنفيذ diskati pair")
				return nil
			}
		}
	}
}

func cmdDevices(dir string) error {
	list, err := control.ListDevices(dir)
	if errors.Is(err, control.ErrNotRunning) {
		fmt.Println("غير مشغّل. شغّله بـ diskati start")
		return nil
	}
	if err != nil {
		return err
	}
	if len(list) == 0 {
		fmt.Println("لا توجد هواتف مرتبطة")
		return nil
	}
	for _, d := range list {
		fmt.Printf("%s  %-40s  أُضيف %s  آخر ظهور %s\n", d.ID, d.Name,
			d.CreatedAt.Local().Format("2006-01-02 15:04"), d.LastSeen.Local().Format("2006-01-02 15:04"))
	}
	return nil
}

func cmdUnpair(dir, id string) error {
	err := control.RemoveDevice(dir, id)
	if errors.Is(err, control.ErrNotRunning) {
		fmt.Println("غير مشغّل. شغّله بـ diskati start ثم أعد المحاولة")
		return nil
	}
	if err != nil {
		return err
	}
	fmt.Println("أُلغي الربط")
	return nil
}

func cmdResetCert(dir string) error {
	if _, err := control.GetStatus(dir); err == nil {
		return errors.New("أوقف البرنامج أولًا: diskati stop")
	}
	if err := pki.Reset(dir); err != nil {
		return err
	}
	store, err := devices.Open(dir)
	if err != nil {
		return err
	}
	n := store.RemoveAll()
	fmt.Printf("أُعيد إنشاء الشهادة عند التشغيل القادم. أُلغي ربط %d هاتف، ويجب ربطها من جديد.\n", n)
	return nil
}

// installArgs builds the fixed reg.exe arguments. No user input is interpolated except our own path.
func installArgs(exe string) []string {
	return []string{
		"add", `HKCU\Software\Microsoft\Windows\CurrentVersion\Run`,
		"/v", runKeyName, "/t", "REG_SZ", "/d", `"` + exe + `" start`, "/f",
	}
}

func uninstallArgs() []string {
	return []string{"delete", `HKCU\Software\Microsoft\Windows\CurrentVersion\Run`, "/v", runKeyName, "/f"}
}

func cmdInstall() error {
	exe, err := os.Executable()
	if err != nil {
		return err
	}
	if out, err := exec.Command("reg", installArgs(exe)...).CombinedOutput(); err != nil {
		return fmt.Errorf("فشل التسجيل: %v: %s", err, out)
	}
	fmt.Println("سيعمل البرنامج عند تسجيل دخولك. لا تنقل الملف بعد التثبيت، وإن نقلته أعد diskati install.")
	return nil
}

func cmdUninstall() error {
	if out, err := exec.Command("reg", uninstallArgs()...).CombinedOutput(); err != nil {
		return fmt.Errorf("فشل الإلغاء: %v: %s", err, out)
	}
	fmt.Println("أُلغي التشغيل التلقائي")
	return nil
}

func fatalIf(err error) {
	if err != nil {
		fatal(err)
	}
}

func fatal(err error) {
	fmt.Fprintln(os.Stderr, "خطأ:", err)
	os.Exit(1)
}
