using System.Diagnostics;
using System.Runtime.InteropServices;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Interop;
using System.Windows.Media;
using System.Windows.Media.Animation;
using System.Windows.Threading;

namespace GrowJEE.Desktop;

/// <summary>Colours and small animation helpers shared by every screen.</summary>
static class Ui
{
    [DllImport("dwmapi.dll")]
    static extern int DwmSetWindowAttribute(IntPtr hwnd, int attr, ref int value, int size);

    static SolidColorBrush Hex(string c) { var b = new SolidColorBrush((Color)ColorConverter.ConvertFromString(c)); b.Freeze(); return b; }
    static LinearGradientBrush Grad(string a, string b, double angle)
    {
        var g = new LinearGradientBrush((Color)ColorConverter.ConvertFromString(a), (Color)ColorConverter.ConvertFromString(b), angle);
        g.Freeze(); return g;
    }

    public static readonly SolidColorBrush Cyan = Hex("#75F4E8");
    public static readonly SolidColorBrush Blue = Hex("#A7AFFF");
    public static readonly SolidColorBrush Main = Hex("#F4F6FF");
    public static readonly SolidColorBrush Muted = Hex("#A8B1C7");
    public static readonly SolidColorBrush Dim = Hex("#7C86A2");
    public static readonly SolidColorBrush Ink = Hex("#080A10");
    public static readonly SolidColorBrush Gold = Hex("#FFD27D");
    public static readonly SolidColorBrush Green = Hex("#5BE3A0");
    public static readonly SolidColorBrush Amber = Hex("#FFC470");
    public static readonly SolidColorBrush Rose = Hex("#FF8FA3");
    public static readonly SolidColorBrush Phys = Hex("#6CC8FF");
    public static readonly SolidColorBrush Chem = Hex("#FF9EC4");
    public static readonly SolidColorBrush MathB = Hex("#B8A4FF");
    public static readonly SolidColorBrush Track = Hex("#26FFFFFF");
    public static readonly Brush Title = Grad("#FFFFFF", "#BCD0FF", 0);
    public static readonly Brush BarFill = Grad("#75F4E8", "#A7AFFF", 0);
    public static readonly Brush ColumnFill = Grad("#75F4E8", "#8B95FF", 90);

    public static readonly IEasingFunction Ease = new CubicEase { EasingMode = EasingMode.EaseOut };

    public static Brush Subject(string s) => s switch { "Physics" => Phys, "Chemistry" => Chem, "Mathematics" => MathB, _ => Blue };

    /// <summary>Dark title bar so the window frame matches the theme.</summary>
    public static void DarkTitle(Window w)
    {
        w.SourceInitialized += (_, _) =>
        {
            try
            {
                var h = new WindowInteropHelper(w).Handle;
                int on = 1;
                if (DwmSetWindowAttribute(h, 20, ref on, sizeof(int)) != 0) DwmSetWindowAttribute(h, 19, ref on, sizeof(int));
            }
            catch { }
        };
    }

    public static TranslateTransform Shift(UIElement el)
    {
        if (el.RenderTransform is TranslateTransform { IsFrozen: false } tt) return tt;
        tt = new TranslateTransform();
        el.RenderTransform = tt;
        return tt;
    }

    /// <summary>Fade + rise into place, staggered by index.</summary>
    public static void Reveal(UIElement el, int index, double rise = 16)
    {
        var tt = Shift(el);
        var delay = TimeSpan.FromMilliseconds(Math.Min(index, 12) * 55);
        el.Opacity = 0;
        tt.Y = rise;
        var fade = new DoubleAnimation(0, 1, TimeSpan.FromMilliseconds(420)) { BeginTime = delay, EasingFunction = Ease };
        var slide = new DoubleAnimation(rise, 0, TimeSpan.FromMilliseconds(520)) { BeginTime = delay, EasingFunction = Ease };
        fade.Completed += (_, _) => { el.BeginAnimation(UIElement.OpacityProperty, null); el.Opacity = 1; };
        slide.Completed += (_, _) => { tt.BeginAnimation(TranslateTransform.YProperty, null); tt.Y = 0; };
        el.BeginAnimation(UIElement.OpacityProperty, fade);
        tt.BeginAnimation(TranslateTransform.YProperty, slide);
    }

    /// <summary>Lift and light up a glass card under the cursor.</summary>
    public static void Hover(Border b, double lift = -3)
    {
        var tt = Shift(b);
        b.MouseEnter += (_, _) =>
        {
            b.Background = (Brush)Application.Current.FindResource("GlassFillHover");
            b.BorderBrush = Cyan;
            tt.BeginAnimation(TranslateTransform.YProperty, new DoubleAnimation(lift, TimeSpan.FromMilliseconds(170)) { EasingFunction = Ease });
        };
        b.MouseLeave += (_, _) =>
        {
            b.ClearValue(Border.BackgroundProperty);
            b.ClearValue(Border.BorderBrushProperty);
            tt.BeginAnimation(TranslateTransform.YProperty, new DoubleAnimation(0, TimeSpan.FromMilliseconds(240)) { EasingFunction = Ease });
        };
    }

    /// <summary>Count a number up from zero. NaN renders as an em dash.</summary>
    public static void CountUp(TextBlock tb, double to, Func<double, string> fmt, bool animate, int ms = 900, int delay = 200)
    {
        if (double.IsNaN(to)) { tb.Text = "—"; return; }
        if (!animate || to == 0) { tb.Text = fmt(to); return; }
        tb.Text = fmt(0);
        var sw = Stopwatch.StartNew();
        var t = new DispatcherTimer(DispatcherPriority.Render) { Interval = TimeSpan.FromMilliseconds(16) };
        t.Tick += (_, _) =>
        {
            var el = sw.ElapsedMilliseconds - delay;
            if (el < 0) return;
            double p = Math.Min(1, el / (double)ms);
            tb.Text = fmt(to * (1 - Math.Pow(1 - p, 3)));
            if (p >= 1) t.Stop();
        };
        t.Start();
    }

    /// <summary>Horizontal progress bar whose fill grows to its value.</summary>
    public static FrameworkElement Bar(double ratio, double height, Brush fill, bool animate, int delayMs = 0)
    {
        ratio = double.IsNaN(ratio) ? 0 : Math.Clamp(ratio, 0, 1);
        var g = new Grid { Height = height, Margin = new Thickness(0, 6, 0, 0) };
        g.Children.Add(new Border { Background = Track, CornerRadius = new CornerRadius(height / 2) });
        var f = new Border { Background = fill, CornerRadius = new CornerRadius(height / 2), HorizontalAlignment = HorizontalAlignment.Left, Width = 0 };
        g.Children.Add(f);
        bool started = false, done = !animate;
        g.SizeChanged += (_, _) =>
        {
            if (g.ActualWidth <= 0) return;
            double w = ratio <= 0 ? 0 : Math.Max(height, g.ActualWidth * ratio);
            if (!started)
            {
                started = true;
                if (!animate) { f.Width = w; return; }
                var a = new DoubleAnimation(0, w, TimeSpan.FromMilliseconds(950)) { BeginTime = TimeSpan.FromMilliseconds(delayMs + 250), EasingFunction = Ease };
                a.Completed += (_, _) => { done = true; f.BeginAnimation(FrameworkElement.WidthProperty, null); f.Width = Math.Max(ratio <= 0 ? 0 : height, g.ActualWidth * ratio); };
                f.BeginAnimation(FrameworkElement.WidthProperty, a);
            }
            else if (done) f.Width = w;
        };
        return g;
    }
}
