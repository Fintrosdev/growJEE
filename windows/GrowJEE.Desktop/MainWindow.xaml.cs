using Microsoft.Win32;
using System.Diagnostics;
using System.Globalization;
using System.Net.Http;
using System.Net.Http.Headers;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using System.Text.Json.Nodes;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Controls.Primitives;
using System.Windows.Input;
using System.Windows.Media;
using System.Windows.Media.Animation;

namespace GrowJEE.Desktop;

public partial class MainWindow : Window
{
    const string Api = "https://filbyqskhaivvimxudjr.supabase.co";
    const string PublishableKey = "sb_publishable_U8rPRHcKY3usIzTKNW47LQ_1e1uwqoS";
    static readonly HttpClient Http = new() { Timeout = TimeSpan.FromSeconds(25) };
    static readonly string[] Pages = ["Dashboard", "Chapter Master", "Daily Study Log", "Question Bank Log", "Mock Test Analytics", "Mistake Register", "Revision Scheduler", "Target Planner"];
    static readonly Dictionary<string, string> Icons = new()
    {
        ["Dashboard"] = "✦", ["Chapter Master"] = "◈", ["Daily Study Log"] = "◷", ["Question Bank Log"] = "▤",
        ["Mock Test Analytics"] = "◉", ["Mistake Register"] = "⌁", ["Revision Scheduler"] = "↻", ["Target Planner"] = "◎"
    };
    static readonly string[] Subjects = ["Physics", "Chemistry", "Mathematics"];
    static readonly (string Name, string Key)[] DataPages = [("Daily Study Log", "daily"), ("Question Bank Log", "questions"), ("Mock Test Analytics", "mocks"), ("Mistake Register", "mistakes"), ("Revision Scheduler", "revisions"), ("Target Planner", "targets")];

    JsonObject Data = new();
    JsonObject? Session;
    string CurrentPage = "Dashboard";
    string ChapterFilter = "All";
    string ChapterSearch = "";
    bool Syncing;
    bool Animate = true;
    int toastVersion;
    readonly List<UIElement> Rev = new();

    string DataFolder => Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "GrowJEE");
    string DataPath => Path.Combine(DataFolder, "progress.json");
    string SessionPath => Path.Combine(DataFolder, "session.bin");

    public MainWindow()
    {
        InitializeComponent();
        Ui.DarkTitle(this);
        Directory.CreateDirectory(DataFolder);
        Data = LoadLocal();
        Session = ReadSession();
        DateText.Text = DateTime.Now.ToString("ddd, d MMM yyyy", CultureInfo.InvariantCulture).ToUpperInvariant();
        BuildNav();
        Render(Session is null);
    }

    async void Window_Loaded(object sender, RoutedEventArgs e) { if (Session is not null) await LoadCloud(); }

    // ───────────────────────── JSON helpers ─────────────────────────
    JsonArray Arr(JsonNode? node) => node as JsonArray ?? new JsonArray();
    JsonObject Obj(JsonNode? node) => node as JsonObject ?? new JsonObject();
    string S(JsonNode? node, string key, string fallback = "") => node is JsonObject o && o[key] is JsonValue v && v.TryGetValue<string>(out var s) ? s ?? fallback : node is JsonObject ob && ob[key] is JsonValue val ? val.ToString() : fallback;
    double N(JsonNode? node, string key) => double.TryParse(S(node, key, "0"), NumberStyles.Any, CultureInfo.InvariantCulture, out var n) ? n : 0;
    static int Stars(double n) => n < 20 ? 0 : n < 40 ? 1 : n < 60 ? 2 : n < 80 ? 3 : n < 100 ? 4 : 5;
    static string StarText(double n) => new string('★', Stars(n)) + new string('☆', 5 - Stars(n)) + $"  {Stars(n)}/5";
    static Brush PctBrush(double p) => p >= 75 ? Ui.Green : p >= 45 ? Ui.Amber : Ui.Rose;
    static bool TryDate(string s, out DateTime d) => DateTime.TryParse(s, CultureInfo.InvariantCulture, DateTimeStyles.None, out d);

    string Key(string p) => DataPages.FirstOrDefault(x => x.Name == p).Key ?? "daily";

    JsonArray Records(string key)
    {
        if (Data["records"] is not JsonObject r) { r = new JsonObject(); Data["records"] = r; }
        if (r[key] is JsonArray a) return a;
        var n = new JsonArray();
        r[key] = n;
        return n;
    }

    // ───────────────────────── Navigation & page shell ─────────────────────────
    void BuildNav()
    {
        foreach (var p in Pages)
        {
            var content = new StackPanel { Orientation = Orientation.Horizontal };
            content.Children.Add(Txt(Icons[p], 15, null, null, new Thickness(0, 0, 0, 0)));
            ((TextBlock)content.Children[0]).Width = 32;
            content.Children.Add(new TextBlock { Text = p });
            var rb = new RadioButton { Content = content, Tag = p, GroupName = "nav", Style = (Style)FindResource("NavItem"), IsChecked = p == CurrentPage };
            rb.Click += (_, _) => Go(p);
            Navigation.Children.Add(rb);
        }
    }

    void Go(string page)
    {
        CurrentPage = page;
        foreach (var child in Navigation.Children) if (child is RadioButton r) r.IsChecked = (string)r.Tag == page;
        PageScroll.ScrollToTop();
        Render(true);
    }

    void Render(bool animate = false)
    {
        Animate = animate;
        Rev.Clear();
        PageSubtitle.Text = CurrentPage == "Dashboard" ? "Your preparation overview" : CurrentPage;
        PageContent.Children.Clear();
        if (CurrentPage == "Dashboard") Dashboard();
        else if (CurrentPage == "Chapter Master") ChapterPage();
        else RecordsPage(CurrentPage);
        UpdateCloud();
        if (animate) for (int i = 0; i < Rev.Count; i++) Ui.Reveal(Rev[i], i);
    }

    void UpdateCloud()
    {
        CloudStatus.Text = Session is null ? "Local mode · this PC" : Syncing ? "Syncing…" : "Cloud · " + S(Obj(Session["user"]), "email", "signed in");
        CloudDot.Fill = Session is null ? Ui.Dim : Syncing ? Ui.Amber : Ui.Green;
    }

    // ───────────────────────── Small UI builders ─────────────────────────
    TextBlock Txt(string text, double size = 13, Brush? fg = null, FontWeight? weight = null, Thickness? margin = null, bool wrap = false) => new()
    {
        Text = text, FontSize = size, Foreground = fg ?? Ui.Main, FontWeight = weight ?? FontWeights.Normal,
        Margin = margin ?? new Thickness(0), TextWrapping = wrap ? TextWrapping.Wrap : TextWrapping.NoWrap
    };

    T R<T>(T el) where T : UIElement { Rev.Add(el); return el; }

    void Heading(string title, string detail)
    {
        PageContent.Children.Add(R(new TextBlock { Text = title, FontSize = 32, FontWeight = FontWeights.Bold, Foreground = Ui.Title, Margin = new Thickness(2, 0, 0, 4) }));
        PageContent.Children.Add(R(Txt(detail, 13, Ui.Muted, null, new Thickness(3, 0, 0, 18), true)));
    }

    Border Card(string title, string detail = "", Panel? host = null)
    {
        var p = new StackPanel();
        if (title != "") p.Children.Add(Txt(title, 16, null, FontWeights.SemiBold));
        if (detail != "") p.Children.Add(Txt(detail, 11.5, Ui.Muted, null, new Thickness(0, 4, 0, 2), true));
        var b = new Border { Style = (Style)FindResource("Card"), Child = p };
        (host ?? PageContent).Children.Add(b);
        Rev.Add(b);
        return b;
    }

    StackPanel CardContent(Border b) => (StackPanel)b.Child;

    Button ActionButton(string text, Action action, bool primary = false)
    {
        var b = new Button { Content = text, HorizontalAlignment = HorizontalAlignment.Left, MinWidth = 150, Margin = new Thickness(0, 0, 10, 12) };
        if (primary) b.Style = (Style)FindResource("PrimaryButton");
        b.Click += (_, _) => action();
        return b;
    }

    void Metric(UniformGrid host, string title, string sub, double value, Func<double, string> fmt, Brush tint)
    {
        var s = new StackPanel();
        s.Children.Add(Txt(title, 10, Ui.Muted, FontWeights.Bold));
        var v = Txt("", 30, tint, FontWeights.Bold, new Thickness(0, 6, 0, 0));
        Ui.CountUp(v, value, fmt, Animate);
        s.Children.Add(v);
        s.Children.Add(Txt(sub, 11, Ui.Dim, null, new Thickness(0, 2, 0, 0)));
        var b = new Border { Style = (Style)FindResource("Card"), Padding = new Thickness(18, 15, 16, 14), Margin = new Thickness(0, 0, 12, 12), CornerRadius = new CornerRadius(18), Child = s };
        host.Children.Add(b);
        Ui.Hover(b);
        Rev.Add(b);
    }

    void Row(Border card, string title, string detail, string right = "", Brush? tint = null, Brush? rightBrush = null)
    {
        var g = new Grid { Margin = new Thickness(0, 11, 0, 0) };
        g.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        g.ColumnDefinitions.Add(new ColumnDefinition());
        g.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        g.Children.Add(new Border { Width = 8, Height = 8, CornerRadius = new CornerRadius(4), Background = tint ?? Ui.Blue, VerticalAlignment = VerticalAlignment.Top, Margin = new Thickness(0, 5, 11, 0) });
        var t = new StackPanel();
        var title1 = Txt(title, 13, null, FontWeights.SemiBold);
        title1.TextTrimming = TextTrimming.CharacterEllipsis;
        t.Children.Add(title1);
        t.Children.Add(Txt(detail, 11, Ui.Muted, null, new Thickness(0, 2, 0, 0), true));
        Grid.SetColumn(t, 1);
        g.Children.Add(t);
        var r = Txt(right, 12, rightBrush ?? Ui.Cyan, FontWeights.SemiBold, new Thickness(10, 0, 0, 0));
        r.VerticalAlignment = VerticalAlignment.Center;
        Grid.SetColumn(r, 2);
        g.Children.Add(r);
        CardContent(card).Children.Add(g);
    }

    void ProgressRow(Border card, string label, string right, double ratio, Brush fill, int delay)
    {
        var s = new StackPanel { Margin = new Thickness(0, 13, 0, 0) };
        var g = new Grid();
        g.Children.Add(Txt(label, 12.5, null, FontWeights.SemiBold));
        var r = Txt(right, 11.5, Ui.Muted);
        r.HorizontalAlignment = HorizontalAlignment.Right;
        g.Children.Add(r);
        s.Children.Add(g);
        s.Children.Add(Ui.Bar(ratio, 8, fill, Animate, delay));
        CardContent(card).Children.Add(s);
    }

    void Empty(Border card, string title, string detail)
    {
        var s = new StackPanel { Margin = new Thickness(0, 12, 0, 0) };
        s.Children.Add(Txt(title, 13, Ui.Muted, FontWeights.SemiBold));
        s.Children.Add(Txt(detail, 11.5, Ui.Dim, null, new Thickness(0, 2, 0, 0), true));
        CardContent(card).Children.Add(s);
    }

    // ───────────────────────── Dashboard ─────────────────────────
    void Dashboard()
    {
        var daily = Records("daily"); var questions = Records("questions"); var mocks = Records("mocks"); var revisions = Records("revisions"); var master = Arr(Data["master"]);
        double mins = daily.Sum(x => N(x, "Duration (min)")), attempted = questions.Sum(x => N(x, "Attempted")), correct = questions.Sum(x => N(x, "Correct"));

        int hr = DateTime.Now.Hour;
        string greeting = hr < 5 ? "Burning the midnight oil" : hr < 12 ? "Good morning" : hr < 17 ? "Good afternoon" : hr < 22 ? "Good evening" : "Late-night focus";
        Heading(greeting, DateTime.Now.ToString("dddd, d MMMM", CultureInfo.InvariantCulture) + "  ·  Only the records you enter appear here.");

        var actions = new StackPanel { Orientation = Orientation.Horizontal };
        actions.Children.Add(ActionButton("＋  Add a study entry", () => { Go("Daily Study Log"); Edit("Daily Study Log", null); }, true));
        actions.Children.Add(ActionButton("＋  Log a mock test", () => { Go("Mock Test Analytics"); Edit("Mock Test Analytics", null); }));
        PageContent.Children.Add(R(actions));

        // derived numbers
        var studied = new HashSet<DateTime>();
        foreach (var x in daily) if (TryDate(S(x, "Date"), out var d)) studied.Add(d.Date);
        int streak = 0; var cur = DateTime.Today;
        if (!studied.Contains(cur)) cur = cur.AddDays(-1);
        while (studied.Contains(cur)) { streak++; cur = cur.AddDays(-1); }

        var pcts = new List<double>();
        foreach (var m in mocks) { double t = N(m, "Total max"); if (t > 0) pcts.Add(N(m, "Total score") / t * 100); }

        var due = revisions.Where(x => S(x, "Status") != "Completed" && TryDate(S(x, "Due date"), out var dd) && dd.Date <= DateTime.Today).OrderBy(x => S(x, "Due date")).ToList();

        var grid = new UniformGrid { Columns = 3, Margin = new Thickness(0, 0, -12, 0) };
        Metric(grid, "STUDY TIME", $"{daily.Count} sessions logged", mins / 60, v => $"{v:0.0} h", Ui.Cyan);
        Metric(grid, "QUESTIONS", $"{correct:N0} answered correctly", attempted, v => Math.Round(v).ToString("N0"), Ui.Blue);
        Metric(grid, "ACCURACY", attempted == 0 ? "No questions yet" : "across all subjects", attempted == 0 ? double.NaN : Math.Round(correct / attempted * 100), v => $"{Math.Round(v):0}%", Ui.Green);
        Metric(grid, "MOCK TESTS", pcts.Count == 0 ? "None logged yet" : $"average {pcts.Average():0}% score", mocks.Count, v => Math.Round(v).ToString("N0"), Ui.Chem);
        Metric(grid, "STUDY STREAK", streak == 0 ? "Log today to start one" : "consecutive days", streak, v => $"{Math.Round(v):0} d", Ui.Amber);
        Metric(grid, "REVISIONS DUE", due.Count == 0 ? "All caught up" : "planned up to today", due.Count, v => Math.Round(v).ToString("N0"), due.Count == 0 ? Ui.Green : Ui.Rose);
        PageContent.Children.Add(grid);

        var cols = new Grid();
        cols.ColumnDefinitions.Add(new ColumnDefinition());
        cols.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(16) });
        cols.ColumnDefinitions.Add(new ColumnDefinition());
        var left = new StackPanel(); var right = new StackPanel();
        Grid.SetColumn(right, 2);
        cols.Children.Add(left); cols.Children.Add(right);
        PageContent.Children.Add(cols);

        // left: study rhythm, chapter progress, accuracy
        var weekHours = Enumerable.Range(0, 7).Sum(i => daily.Where(x => TryDate(S(x, "Date"), out var dt) && dt.Date == DateTime.Today.AddDays(-i)).Sum(x => N(x, "Duration (min)"))) / 60;
        var study = Card("Study rhythm · last 7 days", $"Hours logged per day  ·  {weekHours:0.0} h this week", left);
        CardContent(study).Children.Add(StudyChart(daily));

        int done = master.Count(c => S(c, "status") == "Completed"), inProgress = master.Count(c => S(c, "status") == "In Progress");
        var progress = Card("Chapter progress", $"{done} of {master.Count} chapters completed  ·  {inProgress} in progress", left);
        CardContent(progress).Children.Add(Ui.Bar(master.Count == 0 ? 0 : (double)done / master.Count, 10, Ui.BarFill, Animate, 0));
        int di = 1;
        foreach (var subj in Subjects)
        {
            var all = master.Where(x => S(x, "subject") == subj).ToList();
            int dn = all.Count(x => S(x, "status") == "Completed");
            ProgressRow(progress, subj, $"{dn} / {all.Count}", all.Count == 0 ? 0 : (double)dn / all.Count, Ui.Subject(subj), di++ * 120);
        }

        var accuracy = Card("Accuracy by subject", "Based on your question records", left);
        foreach (var subj in Subjects)
        {
            var qs = questions.Where(q => S(q, "Subject") == subj).ToList();
            double a = qs.Sum(q => N(q, "Attempted")), c = qs.Sum(q => N(q, "Correct"));
            ProgressRow(accuracy, subj, a == 0 ? "No entries yet" : $"{Math.Round(c / a * 100):0}%  ·  {c:0}/{a:0} correct", a == 0 ? 0 : c / a, Ui.Subject(subj), di++ * 120);
        }

        // right: focus chapters, revisions, recent mocks
        var weak = master.Where(c => S(c, "mockMarks") != "" && Stars(N(c, "mockMarks")) <= 2).OrderBy(c => N(c, "mockMarks")).Take(8).ToList();
        var focus = Card("Focus chapters", "Lowest entered marks · stars out of five", right);
        if (weak.Count == 0) Empty(focus, "No scored chapters yet", "Add real marks in Chapter Master to see priorities.");
        else foreach (var c in weak) Row(focus, S(c, "chapter"), $"{S(c, "subject")} · {N(c, "mockMarks"):0}/100", $"{new string('★', Stars(N(c, "mockMarks")))}{new string('☆', 5 - Stars(N(c, "mockMarks")))}", Ui.Subject(S(c, "subject")), Ui.Gold);

        if (due.Count > 0)
        {
            var dueCard = Card("Revisions due", "Planned for today or earlier", right);
            foreach (var x in due.Take(6)) Row(dueCard, S(x, "Chapter", "Revision"), $"{S(x, "Subject")} · revision {S(x, "Revision number", "—")}", S(x, "Due date"), Ui.Subject(S(x, "Subject")), Ui.Rose);
        }

        var recent = Card("Recent mock tests", "Only tests you have entered", right);
        if (mocks.Count == 0) Empty(recent, "No mock tests yet", "Add your first mock test when ready.");
        else foreach (var m in mocks.OrderByDescending(x => S(x, "Date")).Take(5))
        {
            double mx = N(m, "Total max"), sc = N(m, "Total score"); double pc = mx > 0 ? sc / mx * 100 : 0;
            Row(recent, S(m, "Test name", "Mock test"), $"{S(m, "Date")} · {S(m, "Exam")}", $"{S(m, "Total score", "—")} / {S(m, "Total max", "—")}", Ui.Cyan, mx > 0 ? PctBrush(pc) : Ui.Muted);
        }
    }

    FrameworkElement StudyChart(JsonArray daily)
    {
        var g = new Grid { Margin = new Thickness(0, 14, 0, 2) };
        for (int i = 0; i < 7; i++) g.ColumnDefinitions.Add(new ColumnDefinition());
        var days = Enumerable.Range(0, 7).Select(i => DateTime.Today.AddDays(i - 6)).ToArray();
        var vals = days.Select(d => daily.Where(x => TryDate(S(x, "Date"), out var dt) && dt.Date == d.Date).Sum(x => N(x, "Duration (min)")) / 60).ToArray();
        double max = Math.Max(1, vals.Max());
        for (int i = 0; i < 7; i++)
        {
            bool today = days[i].Date == DateTime.Today;
            var cell = new Grid { Margin = new Thickness(5, 0, 5, 0), Background = Brushes.Transparent, ToolTip = $"{days[i]:dddd, d MMM}  ·  {vals[i]:0.0} h" };
            cell.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });
            cell.RowDefinitions.Add(new RowDefinition { Height = new GridLength(96) });
            cell.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });
            var val = Txt(vals[i] > 0 ? vals[i].ToString("0.#") : "", 10, Ui.Muted, null, new Thickness(0, 0, 0, 4));
            val.HorizontalAlignment = HorizontalAlignment.Center;
            var track = new Grid { Width = 30 };
            track.Children.Add(new Border { Background = Ui.Track, CornerRadius = new CornerRadius(8) });
            double h = vals[i] > 0 ? Math.Max(8, 96 * vals[i] / max) : 0;
            var fill = new Border { Background = Ui.ColumnFill, CornerRadius = new CornerRadius(8), VerticalAlignment = VerticalAlignment.Bottom, Height = Animate ? 0 : h };
            if (today) fill.Effect = (System.Windows.Media.Effects.Effect)FindResource("CyanGlow");
            if (Animate && h > 0) fill.BeginAnimation(HeightProperty, new DoubleAnimation(0, h, TimeSpan.FromMilliseconds(650)) { BeginTime = TimeSpan.FromMilliseconds(300 + i * 70), EasingFunction = Ui.Ease });
            track.Children.Add(fill);
            var day = Txt(days[i].ToString("ddd", CultureInfo.InvariantCulture), 10.5, today ? Ui.Cyan : Ui.Muted, today ? FontWeights.Bold : FontWeights.Normal, new Thickness(0, 7, 0, 0));
            day.HorizontalAlignment = HorizontalAlignment.Center;
            Grid.SetRow(track, 1); Grid.SetRow(day, 2);
            cell.Children.Add(val); cell.Children.Add(track); cell.Children.Add(day);
            Grid.SetColumn(cell, i);
            g.Children.Add(cell);
        }
        return g;
    }

    // ───────────────────────── Chapter Master ─────────────────────────
    void ChapterPage()
    {
        Heading("Chapter Master", "Enter actual mock marks. Weak chapters sort to the top.");
        var master = Arr(Data["master"]);

        var legend = Card("Star scale", "Mock marks out of 100");
        var wrap = new WrapPanel { Margin = new Thickness(0, 10, 0, 0) };
        foreach (var (range, st) in new[] { ("< 20", 0), ("20–39", 1), ("40–59", 2), ("60–79", 3), ("80–99", 4), ("100", 5) })
        {
            var chip = new Border { Background = (Brush)new BrushConverter().ConvertFromString("#14FFFFFF")!, CornerRadius = new CornerRadius(10), Padding = new Thickness(11, 5, 11, 5), Margin = new Thickness(0, 0, 8, 6) };
            var sp = new StackPanel { Orientation = Orientation.Horizontal };
            sp.Children.Add(Txt(range + "  ", 11.5, Ui.Muted));
            sp.Children.Add(Txt(new string('★', st) + new string('☆', 5 - st), 11.5, Ui.Gold));
            chip.Child = sp;
            wrap.Children.Add(chip);
        }
        CardContent(legend).Children.Add(wrap);

        var bar = new Grid { Margin = new Thickness(0, 2, 0, 6) };
        bar.ColumnDefinitions.Add(new ColumnDefinition());
        bar.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        var chips = new WrapPanel();
        var search = new TextBox { Text = ChapterSearch, Tag = "Search chapters…", Width = 250, Margin = new Thickness(0, 0, 0, 8), VerticalAlignment = VerticalAlignment.Top };
        Grid.SetColumn(search, 1);
        bar.Children.Add(chips); bar.Children.Add(search);
        PageContent.Children.Add(R(bar));

        PageContent.Children.Add(R(ActionButton("＋  Add chapter", () => Edit("Chapter Master", null), true)));

        var count = Txt("", 11.5, Ui.Dim, null, new Thickness(3, 0, 0, 8));
        PageContent.Children.Add(R(count));
        var list = new StackPanel();
        PageContent.Children.Add(R(list));

        void Fill(bool anim)
        {
            list.Children.Clear();
            var items = Arr(Data["master"]).Where(c => (ChapterFilter == "All" || S(c, "subject") == ChapterFilter) && (ChapterSearch == "" || S(c, "chapter").Contains(ChapterSearch, StringComparison.OrdinalIgnoreCase)))
                .OrderBy(x => S(x, "mockMarks") == "" ? 101 : N(x, "mockMarks")).ToList();
            count.Text = $"{items.Count} chapter{(items.Count == 1 ? "" : "s")}";
            if (items.Count == 0) list.Children.Add(Txt("No chapters match.", 13, Ui.Muted, null, new Thickness(3, 8, 0, 0)));
            int i = 0;
            foreach (var c in items)
            {
                var node = (JsonObject)c!;
                string status = S(c, "status", "Not Started");
                var card = ItemCard(list, S(c, "chapter"),
                    $"{S(c, "subject")} · {(S(c, "mockMarks") == "" ? "Not rated" : $"{N(c, "mockMarks"):0}/100 · {StarText(N(c, "mockMarks"))}")}",
                    () => Edit("Chapter Master", node), () => Delete("master", node), Ui.Subject(S(c, "subject")),
                    status, status == "Completed" ? Ui.Green : status == "In Progress" ? Ui.Amber : Ui.Dim);
                if (anim && i < 14) Ui.Reveal(card, i);
                i++;
            }
        }

        foreach (var f in new[] { "All", "Physics", "Chemistry", "Mathematics" })
        {
            int cnt = f == "All" ? master.Count : master.Count(c => S(c, "subject") == f);
            var chip = new RadioButton { Content = $"{f}  ·  {cnt}", GroupName = "chapterFilter", Style = (Style)FindResource("Chip"), IsChecked = ChapterFilter == f };
            chip.Click += (_, _) => { ChapterFilter = f; Fill(true); };
            chips.Children.Add(chip);
        }
        search.TextChanged += (_, _) => { ChapterSearch = search.Text.Trim(); Fill(false); };
        Fill(false);
    }

    // ───────────────────────── Record pages ─────────────────────────
    void RecordsPage(string page)
    {
        Heading(page, Description(page));
        string key = Key(page);
        var records = Records(key);
        PageContent.Children.Add(R(ActionButton("＋  Add entry", () => Edit(page, null), true)));
        if (records.Count == 0) { Card("Nothing logged yet", "Your records sync to your account when you sign in."); return; }
        foreach (var r in records.Reverse().ToArray())
        {
            var record = (JsonObject)r!;
            var (badge, badgeBrush) = Badge(page, r);
            var card = ItemCard(PageContent, RecordTitle(page, r), RecordSubtitle(page, r),
                () => { if (page == "Mock Test Analytics") MockMenu(record); else Edit(page, record); },
                () => Delete(key, record),
                page == "Mock Test Analytics" ? Ui.Cyan : Ui.Subject(S(r, "Subject")), badge, badgeBrush);
            Rev.Add(card);
        }
    }

    (string, Brush?) Badge(string p, JsonNode? x)
    {
        switch (p)
        {
            case "Daily Study Log": return ($"{S(x, "Duration (min)", "0")} min", Ui.Cyan);
            case "Question Bank Log":
                double a = N(x, "Attempted");
                return a > 0 ? ($"{Math.Round(N(x, "Correct") / a * 100):0}%", PctBrush(N(x, "Correct") / a * 100)) : ("", null);
            case "Mock Test Analytics":
                double t = N(x, "Total max");
                return t > 0 ? ($"{Math.Round(N(x, "Total score") / t * 100):0}%", PctBrush(N(x, "Total score") / t * 100)) : ("", null);
            case "Mistake Register":
                return S(x, "Resolved?") == "Yes" ? ("Resolved", Ui.Green) : (S(x, "Mistake type"), Ui.Amber);
            case "Revision Scheduler":
                var st = S(x, "Status");
                return (st, st == "Completed" ? Ui.Green : st == "In Progress" ? Ui.Amber : Ui.Blue);
            default:
                double tv = N(x, "Target value");
                return tv > 0 ? ($"{Math.Round(N(x, "Actual value") / tv * 100):0}%", PctBrush(N(x, "Actual value") / tv * 100)) : ("", null);
        }
    }

    string Description(string p) => p switch { "Daily Study Log" => "Track focused sessions.", "Question Bank Log" => "Track attempts and accuracy.", "Mock Test Analytics" => "Record scores, attach papers and relate mistakes.", "Mistake Register" => "Link errors to the mock where they happened.", "Revision Scheduler" => "Plan spaced reviews.", _ => "Set weekly targets and record actuals." };
    string RecordTitle(string p, JsonNode? x) => p switch { "Daily Study Log" => S(x, "Chapter", S(x, "Study type", "Study session")), "Question Bank Log" => S(x, "Chapter", S(x, "Source / set", "Question practice")), "Mock Test Analytics" => S(x, "Test name", "Mock test"), "Mistake Register" => S(x, "Chapter", "Mistake"), "Revision Scheduler" => S(x, "Chapter", "Revision"), _ => S(x, "Target type", "Weekly target") };
    string RecordSubtitle(string p, JsonNode? x)
    {
        string date = S(x, "Date", S(x, "Due date", S(x, "Week starting")));
        if (p == "Mock Test Analytics") return $"{date} · {S(x, "Exam")} · {S(x, "Total score", "—")} / {S(x, "Total max", "—")} · {Linked(S(x, "id"))} linked errors";
        if (p == "Mistake Register") return $"{date} · {S(x, "Subject")} · {S(x, "Mistake type")} · {MockName(S(x, "Mock test"))}";
        if (p == "Daily Study Log") return $"{date} · {S(x, "Subject")} · {S(x, "Study type")}";
        if (p == "Question Bank Log") return $"{date} · {S(x, "Subject")} · {S(x, "Correct", "0")} / {S(x, "Attempted", "0")} correct";
        return $"{date} · {S(x, "Subject")} · {S(x, "Status", S(x, "Revision number"))}";
    }

    Border ItemCard(Panel host, string title, string detail, Action open, Action delete, Brush accent, string badge = "", Brush? badgeBrush = null)
    {
        var g = new Grid();
        g.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        g.ColumnDefinitions.Add(new ColumnDefinition());
        g.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        g.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        g.Children.Add(new Border { Width = 4, CornerRadius = new CornerRadius(2), Background = accent, Margin = new Thickness(0, 2, 13, 2) });

        var t = new StackPanel { VerticalAlignment = VerticalAlignment.Center };
        var ttl = Txt(title, 14, null, FontWeights.SemiBold);
        ttl.TextTrimming = TextTrimming.CharacterEllipsis;
        t.Children.Add(ttl);
        t.Children.Add(Txt(detail, 11.5, Ui.Muted, null, new Thickness(0, 4, 0, 0), true));
        Grid.SetColumn(t, 1);
        g.Children.Add(t);

        if (badge != "")
        {
            var bb = badgeBrush ?? Ui.Muted;
            var chip = new Border { Background = (Brush)new BrushConverter().ConvertFromString("#1AFFFFFF")!, BorderBrush = bb, BorderThickness = new Thickness(1), CornerRadius = new CornerRadius(10), Padding = new Thickness(10, 3, 10, 3), VerticalAlignment = VerticalAlignment.Center, Margin = new Thickness(10, 0, 4, 0), Child = Txt(badge, 11, bb, FontWeights.SemiBold) };
            Grid.SetColumn(chip, 2);
            g.Children.Add(chip);
        }

        var menu = new Button { Content = "···", Style = (Style)FindResource("IconButton"), Margin = new Thickness(6, 0, 0, 0), VerticalAlignment = VerticalAlignment.Center };
        Grid.SetColumn(menu, 3);
        g.Children.Add(menu);

        var b = new Border { Style = (Style)FindResource("Card"), Padding = new Thickness(14, 12, 10, 12), Margin = new Thickness(0, 0, 0, 9), CornerRadius = new CornerRadius(16), Child = g, Cursor = Cursors.Hand };
        b.MouseLeftButtonUp += (_, _) => open();
        Ui.Hover(b, -2);
        menu.Click += (_, _) =>
        {
            var m = new ContextMenu();
            var e = new MenuItem { Header = "Edit / details" }; e.Click += (_, _) => open();
            var d = new MenuItem { Header = "Delete", Foreground = Ui.Rose }; d.Click += (_, _) => delete();
            m.Items.Add(e); m.Items.Add(d);
            menu.ContextMenu = m; m.PlacementTarget = menu; m.IsOpen = true;
        };
        host.Children.Add(b);
        return b;
    }

    // ───────────────────────── Add / edit form ─────────────────────────
    string[][] Fields(string p) => p switch
    {
        "Chapter Master" => [["Subject", "subject"], ["Chapter", "text"], ["Syllabus list", "text"], ["Status", "status"], ["Confidence", "confidence"], ["Recent mock mark / 100", "number"], ["Notes", "textarea"]],
        "Daily Study Log" => [["Date", "date"], ["Subject", "subject"], ["Chapter", "chapter"], ["Study type", "studytype"], ["Duration (min)", "number"], ["Notes", "textarea"]],
        "Question Bank Log" => [["Date", "date"], ["Subject", "subject"], ["Chapter", "chapter"], ["Difficulty", "difficulty"], ["Source / set", "text"], ["Attempted", "number"], ["Correct", "number"], ["Time (min)", "number"], ["Notes", "textarea"]],
        "Mock Test Analytics" => [["Date", "date"], ["Exam", "exam"], ["Test name", "text"], ["Physics score", "number"], ["Physics max", "number"], ["Chemistry score", "number"], ["Chemistry max", "number"], ["Mathematics score", "number"], ["Mathematics max", "number"], ["Total score", "number"], ["Total max", "number"], ["Duration (min)", "number"], ["Notes", "textarea"]],
        "Mistake Register" => [["Date", "date"], ["Mock test", "mockref"], ["Subject", "subject"], ["Chapter", "chapter"], ["Mistake type", "mistaketype"], ["Question / source", "text"], ["What went wrong", "textarea"], ["Correct approach", "textarea"], ["Resolved?", "yesno"]],
        "Revision Scheduler" => [["Due date", "date"], ["Subject", "subject"], ["Chapter", "chapter"], ["Revision number", "number"], ["Status", "revisionstatus"], ["Completed date", "date"], ["Notes", "textarea"]],
        _ => [["Week starting", "date"], ["Subject", "subject"], ["Target type", "targettype"], ["Target value", "number"], ["Actual value", "number"], ["Notes", "textarea"]]
    };

    string[]? Choices(string t) => t switch { "subject" => Subjects, "exam" => ["JEE Main", "JEE Advanced"], "status" => ["Not Started", "In Progress", "Completed"], "confidence" => ["Low", "Medium", "High"], "difficulty" => ["Easy", "Moderate", "Hard"], "studytype" => ["Concept Study", "Problem Practice", "Revision", "Mock Analysis"], "mistaketype" => ["Conceptual", "Calculation", "Misread", "Time Management", "Guessing", "Test Strategy"], "yesno" => ["No", "Yes"], "revisionstatus" => ["Planned", "In Progress", "Completed"], "targettype" => ["Study Hours", "Questions Attempted"], _ => null };
    string ChapterKey(string x) => x switch { "Subject" => "subject", "Chapter" => "chapter", "Syllabus list" => "source", "Status" => "status", "Confidence" => "confidence", "Recent mock mark / 100" => "mockMarks", _ => "notes" };

    void Edit(string page, JsonObject? current)
    {
        bool chapter = page == "Chapter Master";
        JsonObject edited = current is null ? new JsonObject() : (JsonObject)JsonNode.Parse(current.ToJsonString())!;
        Window win = null!;

        var shell = new DockPanel { Margin = new Thickness(26, 22, 26, 20) };
        var head = new StackPanel { Margin = new Thickness(0, 0, 0, 10) };
        head.Children.Add(Txt((current is null ? "Add" : "Edit") + " · " + page, 20, null, FontWeights.SemiBold));
        head.Children.Add(Txt(current is null ? "Fill in what you know. You can edit it later." : "Update the details and save.", 12, Ui.Muted, null, new Thickness(0, 3, 0, 0)));
        DockPanel.SetDock(head, Dock.Top);
        shell.Children.Add(head);

        var buttons = new StackPanel { Orientation = Orientation.Horizontal, HorizontalAlignment = HorizontalAlignment.Right, Margin = new Thickness(0, 14, 0, 0) };
        var cancel = new Button { Content = "Cancel", IsCancel = true };
        var save = new Button { Content = "Save entry", Style = (Style)FindResource("PrimaryButton"), MinWidth = 120 };
        buttons.Children.Add(cancel); buttons.Children.Add(save);
        DockPanel.SetDock(buttons, Dock.Bottom);
        shell.Children.Add(buttons);

        var scroll = new ScrollViewer { VerticalScrollBarVisibility = ScrollBarVisibility.Auto, HorizontalScrollBarVisibility = ScrollBarVisibility.Disabled };
        var form = new StackPanel { Margin = new Thickness(0, 0, 10, 0) };
        scroll.Content = form;
        shell.Children.Add(scroll);

        var controls = new Dictionary<string, FrameworkElement>();
        foreach (var f in Fields(page))
        {
            string name = f[0], type = f[1], key = chapter ? ChapterKey(name) : name;
            form.Children.Add(Txt(name.ToUpperInvariant(), 10, Ui.Muted, FontWeights.Bold, new Thickness(2, 8, 0, 0)));
            FrameworkElement c;
            if (type == "mockref")
            {
                var items = Records("mocks").Select(x => S(x, "id") + "|" + S(x, "Test name", "Mock test")).Prepend("No mock linked").ToList();
                var cb = new ComboBox { ItemsSource = items, SelectedIndex = 0 };
                string id = S(edited, key);
                for (int i = 1; i < items.Count; i++) if (items[i].StartsWith(id + "|")) cb.SelectedIndex = i;
                cb.ItemTemplate = MockRefTemplate();
                c = cb;
            }
            else if (Choices(type) is string[] opts)
            {
                var cb = new ComboBox { ItemsSource = opts, SelectedIndex = 0 };
                int ix = Array.IndexOf(opts, S(edited, key));
                if (ix >= 0) cb.SelectedIndex = ix;
                c = cb;
            }
            else
            {
                var box = new TextBox { Text = S(edited, key) };
                if (type == "textarea") { box.AcceptsReturn = true; box.TextWrapping = TextWrapping.Wrap; box.MinHeight = 72; box.VerticalScrollBarVisibility = ScrollBarVisibility.Auto; }
                if (type == "date" && string.IsNullOrEmpty(box.Text)) box.Text = DateTime.Today.ToString("yyyy-MM-dd");
                c = box;
            }
            form.Children.Add(c);
            controls[name] = c;
        }

        save.Click += async (_, _) =>
        {
            foreach (var f in Fields(page))
            {
                string name = f[0], type = f[1], key = chapter ? ChapterKey(name) : name;
                string value = controls[name] switch { ComboBox cb => cb.SelectedItem?.ToString() ?? "", TextBox tb => tb.Text.Trim(), _ => "" };
                if (type == "mockref") value = value == "No mock linked" ? "" : value.Split('|')[0];
                edited[key] = value;
            }
            if (chapter && string.IsNullOrWhiteSpace(S(edited, "chapter"))) { Notice(win, "Add a chapter name first."); return; }
            if (chapter && S(edited, "mockMarks") != "" && (N(edited, "mockMarks") < 0 || N(edited, "mockMarks") > 100)) { Notice(win, "Marks must be between 0 and 100."); return; }
            if (page == "Question Bank Log" && N(edited, "Correct") > N(edited, "Attempted")) { Notice(win, "Correct cannot exceed attempted."); return; }

            var target = chapter ? Arr(Data["master"]) : Records(Key(page));
            if (current is null) { edited["id"] = Guid.NewGuid().ToString(); target.Add(edited); }
            else for (int z = 0; z < target.Count; z++) if (S(target[z], "id") == S(current, "id")) { target[z] = edited; break; }

            if (chapter && current is not null && S(edited, "mockMarks") != S(current, "mockMarks") && S(edited, "mockMarks") != "")
                Records("chapterMarks").Add(new JsonObject { ["id"] = Guid.NewGuid().ToString(), ["chapterId"] = S(edited, "id"), ["subject"] = S(edited, "subject"), ["chapter"] = S(edited, "chapter"), ["score"] = N(edited, "mockMarks"), ["date"] = DateTime.Today.ToString("yyyy-MM-dd") });
            if (page == "Mistake Register") edited["Mock test label"] = MockName(S(edited, "Mock test"));

            PersistLocal();
            win.Close();
            Render();
            Toast("Saved");
            await SyncCloud();
        };

        win = GlassWindow((current is null ? "Add " : "Edit ") + page, 560, 740, shell, null, true);
        win.MinHeight = 460;
        win.ShowDialog();
    }

    DataTemplate MockRefTemplate()
    {
        // The combo items are "id|name" – show only the name.
        var tpl = new DataTemplate();
        var tb = new FrameworkElementFactory(typeof(TextBlock));
        tb.SetBinding(TextBlock.TextProperty, new System.Windows.Data.Binding { Converter = new MockLabelConverter() });
        tpl.VisualTree = tb;
        return tpl;
    }

    sealed class MockLabelConverter : System.Windows.Data.IValueConverter
    {
        public object Convert(object value, Type targetType, object parameter, CultureInfo culture)
        {
            var s = value?.ToString() ?? "";
            int i = s.IndexOf('|');
            return i >= 0 ? s[(i + 1)..] : s;
        }
        public object ConvertBack(object value, Type targetType, object parameter, CultureInfo culture) => throw new NotSupportedException();
    }

    // ───────────────────────── Glass dialogs ─────────────────────────
    Window GlassWindow(string title, double width, double height, UIElement content, Window? owner = null, bool resize = false)
    {
        var glow = new System.Windows.Shapes.Ellipse
        {
            Width = 440, Height = 440, HorizontalAlignment = HorizontalAlignment.Right, VerticalAlignment = VerticalAlignment.Top,
            Margin = new Thickness(0, -200, -180, 0), IsHitTestVisible = false,
            Fill = new RadialGradientBrush(Color.FromArgb(0x55, 0x6C, 0xE7, 0xE2), Color.FromArgb(0x00, 0x6C, 0xE7, 0xE2))
        };
        var root = new Grid { ClipToBounds = true };
        root.Children.Add(glow);
        root.Children.Add(content);
        var win = new Window
        {
            Title = title, Owner = owner ?? this, WindowStartupLocation = WindowStartupLocation.CenterOwner, Width = width, Height = height,
            ResizeMode = resize ? ResizeMode.CanResize : ResizeMode.NoResize, ShowInTaskbar = false, UseLayoutRounding = true,
            Background = (Brush)FindResource("Backdrop"), Foreground = (Brush)FindResource("TextMain"), FontFamily = FontFamily, Content = root
        };
        Ui.DarkTitle(win);
        Ui.Reveal(content, 0, 10);
        return win;
    }

    bool Dialog(Window? owner, string title, string message, bool ask, string yes)
    {
        bool result = false;
        Window win = null!;
        var p = new StackPanel { Margin = new Thickness(26, 24, 26, 20) };
        p.Children.Add(Txt(title, 18, null, FontWeights.SemiBold));
        p.Children.Add(Txt(message, 13, Ui.Muted, null, new Thickness(0, 9, 0, 0), true));
        var row = new StackPanel { Orientation = Orientation.Horizontal, HorizontalAlignment = HorizontalAlignment.Right, Margin = new Thickness(0, 20, 0, 0) };
        if (ask) row.Children.Add(new Button { Content = "Cancel", IsCancel = true });
        var ok = new Button { Content = yes, IsDefault = true, MinWidth = 100, Style = (Style)FindResource("PrimaryButton") };
        ok.Click += (_, _) => { result = true; win.Close(); };
        row.Children.Add(ok);
        p.Children.Add(row);
        win = GlassWindow(ask ? "Please confirm" : "GrowJEE", 440, 10, p, owner);
        win.SizeToContent = SizeToContent.Height;
        win.ShowDialog();
        return result;
    }

    void Notice(Window? owner, string message, string title = "GrowJEE") => Dialog(owner, title, message, false, "OK");
    bool Ask(Window? owner, string message, string yes = "Yes") => Dialog(owner, "Are you sure?", message, true, yes);

    void Toast(string message)
    {
        ToastText.Text = message;
        ToastHost.Visibility = Visibility.Visible;
        int v = ++toastVersion;
        var op = new DoubleAnimationUsingKeyFrames();
        op.KeyFrames.Add(new LinearDoubleKeyFrame(0, KeyTime.FromTimeSpan(TimeSpan.Zero)));
        op.KeyFrames.Add(new LinearDoubleKeyFrame(1, KeyTime.FromTimeSpan(TimeSpan.FromMilliseconds(220))));
        op.KeyFrames.Add(new LinearDoubleKeyFrame(1, KeyTime.FromTimeSpan(TimeSpan.FromMilliseconds(2300))));
        op.KeyFrames.Add(new LinearDoubleKeyFrame(0, KeyTime.FromTimeSpan(TimeSpan.FromMilliseconds(2800))));
        op.Completed += (_, _) => { if (v == toastVersion) ToastHost.Visibility = Visibility.Collapsed; };
        ToastHost.BeginAnimation(OpacityProperty, op);
        Ui.Shift(ToastHost).BeginAnimation(TranslateTransform.YProperty, new DoubleAnimation(18, 0, TimeSpan.FromMilliseconds(320)) { EasingFunction = Ui.Ease });
    }

    // ───────────────────────── Account, login & sharing ─────────────────────────
    void Account_Click(object sender, RoutedEventArgs e)
    {
        if (Session is null) { Login(); return; }
        Window win = null!;
        var box = new StackPanel { Margin = new Thickness(26, 24, 26, 20) };
        box.Children.Add(Txt("Account & cloud sync", 20, null, FontWeights.SemiBold));
        box.Children.Add(Txt("Signed in as " + S(Obj(Session["user"]), "email"), 13, Ui.Cyan, FontWeights.SemiBold, new Thickness(0, 6, 0, 0)));
        box.Children.Add(Txt("Your progress syncs to your private account. This uses the same GrowJEE login as Android and the website.", 12, Ui.Muted, null, new Thickness(0, 8, 0, 18), true));

        box.Children.Add(Txt("SHARE WITH YOUR TEACHER", 10, Ui.Muted, FontWeights.Bold));
        var invite = new TextBox { Tag = "Teacher's signed-in email" };
        box.Children.Add(invite);
        box.Children.Add(Txt("Read-only. They can see everything: chapters and marks, daily and question logs, mock scores, mistakes, revisions and targets. It refreshes every time you sync.", 11.5, Ui.Dim, null, new Thickness(0, -2, 0, 10), true));
        var share = new Button { Content = "Share all progress (read-only)", Style = (Style)FindResource("PrimaryButton"), Margin = new Thickness(0, 0, 0, 14), HorizontalAlignment = HorizontalAlignment.Left };
        box.Children.Add(share);

        var outRow = new StackPanel { Orientation = Orientation.Horizontal, HorizontalAlignment = HorizontalAlignment.Right };
        var signout = new Button { Content = "Sign out" };
        var done = new Button { Content = "Done", IsCancel = true, MinWidth = 90 };
        outRow.Children.Add(signout); outRow.Children.Add(done);
        box.Children.Add(outRow);

        win = GlassWindow("Account & cloud sync", 500, 10, box);
        win.SizeToContent = SizeToContent.Height;

        share.Click += async (_, _) =>
        {
            if (string.IsNullOrWhiteSpace(invite.Text)) { Notice(win, "Enter your teacher's account email."); return; }
            share.IsEnabled = false;
            try
            {
                var token = await AccessToken();
                var body = new JsonObject
                {
                    ["student_id"] = S(Obj(Session["user"]), "id"),
                    ["student_email"] = S(Obj(Session["user"]), "email"),
                    ["teacher_email"] = invite.Text.Trim().ToLowerInvariant(),
                    ["progress_snapshot"] = Snapshot(),
                    ["snapshot_updated_at"] = DateTime.UtcNow.ToString("o")
                };
                await ApiRequest("/rest/v1/jee_shares", "POST", body, token, "return=minimal");
                Notice(win, "Shared. Your teacher can sign in on the GrowJEE website with that email and see all your progress.");
            }
            catch (Exception ex) { Notice(win, "Could not share: " + ex.Message); }
            finally { share.IsEnabled = true; }
        };
        signout.Click += async (_, _) =>
        {
            try { await ApiRequest("/auth/v1/logout", "POST", null, await AccessToken()); } catch { }
            Session = null;
            try { File.Delete(SessionPath); } catch { }
            win.Close();
            Render();
        };
        win.ShowDialog();
    }

    void Login()
    {
        Window win = null!;
        var p = new StackPanel { Margin = new Thickness(26, 24, 26, 20) };
        p.Children.Add(Txt("Welcome to GrowJEE", 20, null, FontWeights.SemiBold));
        p.Children.Add(Txt("Same account as your GrowJEE website and Android app.", 12, Ui.Muted, null, new Thickness(0, 4, 0, 16)));
        p.Children.Add(Txt("EMAIL", 10, Ui.Muted, FontWeights.Bold));
        var email = new TextBox { Tag = "you@example.com" };
        p.Children.Add(email);
        p.Children.Add(Txt("PASSWORD", 10, Ui.Muted, FontWeights.Bold));
        var password = new PasswordBox();
        p.Children.Add(password);
        var buttons = new StackPanel { Orientation = Orientation.Horizontal, HorizontalAlignment = HorizontalAlignment.Right, Margin = new Thickness(0, 8, 0, 0) };
        var cancel = new Button { Content = "Cancel", IsCancel = true };
        var create = new Button { Content = "Create account" };
        var login = new Button { Content = "Sign in", IsDefault = true, MinWidth = 110, Style = (Style)FindResource("PrimaryButton") };
        buttons.Children.Add(cancel); buttons.Children.Add(create); buttons.Children.Add(login);
        p.Children.Add(buttons);

        win = GlassWindow("GrowJEE account", 470, 10, p);
        win.SizeToContent = SizeToContent.Height;
        async Task Go2(bool createNew)
        {
            login.IsEnabled = create.IsEnabled = false;
            try { await Authenticate(email.Text, password.Password, createNew, win); }
            finally { login.IsEnabled = create.IsEnabled = true; }
        }
        create.Click += async (_, _) => await Go2(true);
        login.Click += async (_, _) => await Go2(false);
        win.Loaded += (_, _) => email.Focus();
        win.ShowDialog();
    }

    async Task Authenticate(string email, string password, bool create, Window dialog)
    {
        if (string.IsNullOrWhiteSpace(email) || password.Length < 6) { Notice(dialog, "Enter an email and password of at least six characters."); return; }
        try
        {
            var body = new JsonObject { ["email"] = email.Trim(), ["password"] = password };
            Session = await ApiRequest(create ? "/auth/v1/signup" : "/auth/v1/token?grant_type=password", "POST", body) as JsonObject;
            if (Session is null || Session["access_token"] is null) { Session = null; Notice(dialog, "Confirm your email, then sign in."); return; }
            Session["expires_at"] = DateTimeOffset.UtcNow.ToUnixTimeSeconds() + ((long?)Session["expires_in"] ?? 3600);
            SaveSession();
            dialog.Close();
            Render();
            await LoadCloud();
        }
        catch (Exception ex) { Notice(dialog, "Sign-in failed: " + ex.Message); }
    }

    /// <summary>
    /// What a teacher sees. Keeps the original summary fields (so existing readers keep working)
    /// and adds the complete data set: every chapter plus every record list.
    /// </summary>
    JsonObject Snapshot()
    {
        var daily = Records("daily"); var questions = Records("questions"); var mocks = Records("mocks"); var master = Arr(Data["master"]);
        double mins = daily.Sum(x => N(x, "Duration (min)")), attempt = questions.Sum(x => N(x, "Attempted")), correct = questions.Sum(x => N(x, "Correct"));
        var weak = new JsonArray();
        foreach (var c in master.Where(x => S(x, "mockMarks") != "" && Stars(N(x, "mockMarks")) <= 2).OrderBy(x => N(x, "mockMarks")))
            weak.Add(new JsonObject { ["subject"] = S(c, "subject"), ["chapter"] = S(c, "chapter"), ["score"] = N(c, "mockMarks"), ["stars"] = Stars(N(c, "mockMarks")) });
        var recent = new JsonArray();
        foreach (var m in mocks.OrderByDescending(x => S(x, "Date")).Take(8))
            recent.Add(new JsonObject { ["date"] = S(m, "Date"), ["name"] = S(m, "Test name"), ["exam"] = S(m, "Exam"), ["score"] = S(m, "Total score"), ["max"] = S(m, "Total max") });
        var progress = new JsonArray();
        foreach (var subj in Subjects)
        {
            var all = master.Where(x => S(x, "subject") == subj).ToList();
            progress.Add(new JsonObject { ["subject"] = subj, ["complete"] = all.Count(x => S(x, "status") == "Completed"), ["total"] = all.Count });
        }

        // Full copy of everything the student has entered.
        var fullRecords = (JsonObject)Obj(Data["records"]).DeepClone();
        foreach (var m in Arr(fullRecords["mocks"]))
            if (m is JsonObject mo)
                foreach (var k in new[] { "questionPdf", "answerPdf" })
                    if (mo[k] is JsonObject meta) { meta.Remove("localCache"); meta.Remove("key"); } // never leak paths on this PC

        return new JsonObject
        {
            ["version"] = 2,
            ["scope"] = "full",
            ["studyMinutes"] = mins,
            ["questionsAttempted"] = attempt,
            ["questionsCorrect"] = correct,
            ["accuracy"] = attempt == 0 ? null : Math.Round(correct / attempt * 100),
            ["chaptersComplete"] = master.Count(x => S(x, "status") == "Completed"),
            ["chaptersTotal"] = master.Count,
            ["subjectProgress"] = progress,
            ["priorityChapters"] = weak,
            ["mockTests"] = recent,
            ["master"] = master.DeepClone(),
            ["records"] = fullRecords,
            ["generatedAt"] = DateTime.UtcNow.ToString("o")
        };
    }

    // ───────────────────────── Cloud sync ─────────────────────────
    async Task LoadCloud()
    {
        if (Session is null) return;
        Syncing = true; UpdateCloud();
        try
        {
            var token = await AccessToken();
            var id = S(Obj(Session["user"]), "id");
            var rows = await ApiRequest("/rest/v1/jee_user_data?select=payload&user_id=eq." + Uri.EscapeDataString(id), "GET", null, token) as JsonArray;
            if (rows?.Count > 0 && rows[0]?["payload"] is JsonObject payload) Data = (JsonObject)payload.DeepClone();
            else await PushData(token);
            PersistLocal();
            Syncing = false;
            Render(true);
        }
        catch (Exception ex)
        {
            Syncing = false; UpdateCloud();
            Notice(this, "Cloud sync paused: " + ex.Message);
        }
    }

    async Task SyncCloud()
    {
        if (Session is null) return;
        Syncing = true; UpdateCloud();
        try { await PushData(await AccessToken()); await SyncShares(); Syncing = false; UpdateCloud(); }
        catch (Exception ex) { Syncing = false; UpdateCloud(); Notice(this, "Saved on this PC. Cloud sync paused: " + ex.Message); }
    }

    async Task PushData(string token)
    {
        var body = new JsonObject { ["user_id"] = S(Obj(Session?["user"]), "id"), ["payload"] = Data.DeepClone(), ["updated_at"] = DateTime.UtcNow.ToString("o") };
        await ApiRequest("/rest/v1/jee_user_data?on_conflict=user_id", "POST", body, token, "resolution=merge-duplicates,return=minimal");
    }

    async Task SyncShares()
    {
        var query = "/rest/v1/jee_shares?student_id=eq." + Uri.EscapeDataString(S(Obj(Session?["user"]), "id"));
        await ApiRequest(query, "PATCH", new JsonObject { ["progress_snapshot"] = Snapshot(), ["snapshot_updated_at"] = DateTime.UtcNow.ToString("o") }, await AccessToken(), "return=minimal");
    }

    async Task<JsonNode?> ApiRequest(string path, string method, JsonObject? body = null, string? token = null, string? prefer = null)
    {
        using var req = new HttpRequestMessage(new HttpMethod(method), Api + path);
        req.Headers.Add("apikey", PublishableKey);
        if (token is not null) req.Headers.Authorization = new AuthenticationHeaderValue("Bearer", token);
        if (prefer is not null) req.Headers.TryAddWithoutValidation("Prefer", prefer);
        if (body is not null) req.Content = new StringContent(body.ToJsonString(), Encoding.UTF8, "application/json");
        using var response = await Http.SendAsync(req);
        var raw = await response.Content.ReadAsStringAsync();
        if (!response.IsSuccessStatusCode)
        {
            try
            {
                var err = JsonNode.Parse(raw);
                throw new Exception(err?["message"]?.ToString() ?? err?["msg"]?.ToString() ?? err?["error_description"]?.ToString() ?? $"Request failed ({(int)response.StatusCode})");
            }
            catch (JsonException) { throw new Exception($"Request failed ({(int)response.StatusCode})"); }
        }
        return string.IsNullOrWhiteSpace(raw) ? null : JsonNode.Parse(raw);
    }

    async Task<string> AccessToken()
    {
        if (Session is null) throw new Exception("Sign in again.");
        long exp = (long?)Session["expires_at"] ?? 0;
        if (exp > DateTimeOffset.UtcNow.ToUnixTimeSeconds() + 60) return S(Session, "access_token");
        var fresh = await ApiRequest("/auth/v1/token?grant_type=refresh_token", "POST", new JsonObject { ["refresh_token"] = S(Session, "refresh_token") }) as JsonObject ?? throw new Exception("Session expired.");
        Session = fresh;
        Session["expires_at"] = DateTimeOffset.UtcNow.ToUnixTimeSeconds() + ((long?)fresh["expires_in"] ?? 3600);
        SaveSession();
        return S(Session, "access_token");
    }

    // ───────────────────────── Local storage ─────────────────────────
    void PersistLocal() { Directory.CreateDirectory(DataFolder); File.WriteAllText(DataPath, Data.ToJsonString(new JsonSerializerOptions { WriteIndented = true })); }
    JsonObject LoadLocal() { try { if (File.Exists(DataPath)) return JsonNode.Parse(File.ReadAllText(DataPath))!.AsObject(); } catch { } return FreshData(); }

    JsonObject FreshData()
    {
        var master = new JsonArray();
        try
        {
            var chapters = JsonNode.Parse(File.ReadAllText(Path.Combine(AppContext.BaseDirectory, "chapters.json")))!.AsArray();
            for (int i = 0; i < chapters.Count; i++)
            {
                var c = chapters[i]!;
                master.Add(new JsonObject { ["id"] = "c" + i, ["subject"] = S(c, "subject"), ["chapter"] = S(c, "chapter"), ["source"] = S(c, "source"), ["status"] = "Not Started", ["confidence"] = "", ["mockMarks"] = "", ["mockDate"] = "", ["notes"] = "" });
            }
        }
        catch { }
        return new JsonObject
        {
            ["master"] = master,
            ["records"] = new JsonObject { ["daily"] = new JsonArray(), ["questions"] = new JsonArray(), ["mocks"] = new JsonArray(), ["mistakes"] = new JsonArray(), ["revisions"] = new JsonArray(), ["targets"] = new JsonArray(), ["chapterMarks"] = new JsonArray() }
        };
    }

    void SaveSession()
    {
        if (Session is null) return;
        byte[] clear = Encoding.UTF8.GetBytes(Session.ToJsonString());
        File.WriteAllBytes(SessionPath, ProtectedData.Protect(clear, null, DataProtectionScope.CurrentUser));
    }

    JsonObject? ReadSession()
    {
        try
        {
            if (!File.Exists(SessionPath)) return null;
            var clear = ProtectedData.Unprotect(File.ReadAllBytes(SessionPath), null, DataProtectionScope.CurrentUser);
            return JsonNode.Parse(clear) as JsonObject;
        }
        catch { return null; }
    }

    // ───────────────────────── Mock tests: papers & linked mistakes ─────────────────────────
    void MockMenu(JsonObject mock)
    {
        Window win = null!;
        var p = new StackPanel { Margin = new Thickness(26, 24, 26, 20) };
        p.Children.Add(Txt(S(mock, "Test name", "Mock test"), 20, null, FontWeights.SemiBold));
        p.Children.Add(Txt($"{S(mock, "Exam")} · {S(mock, "Date")} · {S(mock, "Total score", "—")} / {S(mock, "Total max", "—")}", 12, Ui.Muted, null, new Thickness(0, 4, 0, 14)));

        Button Act(string text, Func<Task> run, bool primary = false)
        {
            var b = new Button { Content = text, HorizontalContentAlignment = HorizontalAlignment.Left, Margin = new Thickness(0, 4, 0, 4), Padding = new Thickness(16, 11, 16, 11) };
            if (primary) b.Style = (Style)FindResource("PrimaryButton");
            b.Click += async (_, _) => { win.Close(); await run(); };
            return b;
        }
        p.Children.Add(Act("Edit mock", () => { Edit("Mock Test Analytics", mock); return Task.CompletedTask; }, true));
        p.Children.Add(Act($"Linked mistakes  ·  {Linked(S(mock, "id"))}", () => { ShowLinked(mock); return Task.CompletedTask; }));
        p.Children.Add(Act("Attach question paper PDF", () => AttachPdf(mock, "question")));
        p.Children.Add(Act("Attach answer sheet PDF", () => AttachPdf(mock, "answer")));
        if (mock["questionPdf"] is not null) p.Children.Add(Act("Open question paper", () => OpenPdf(mock, "question")));
        if (mock["answerPdf"] is not null) p.Children.Add(Act("Open answer sheet", () => OpenPdf(mock, "answer")));
        p.Children.Add(Txt($"Question paper: {S(Obj(mock["questionPdf"]), "name", "none attached")}\nAnswer sheet: {S(Obj(mock["answerPdf"]), "name", "none attached")}", 11.5, Ui.Dim, null, new Thickness(2, 12, 0, 0), true));

        win = GlassWindow(S(mock, "Test name", "Mock test"), 440, 10, p);
        win.SizeToContent = SizeToContent.Height;
        win.ShowDialog();
    }

    void ShowLinked(JsonObject mock)
    {
        var errors = Records("mistakes").Where(x => S(x, "Mock test") == S(mock, "id")).ToList();
        var p = new StackPanel { Margin = new Thickness(24, 22, 24, 20) };
        p.Children.Add(Txt("Mistakes linked to " + S(mock, "Test name", "mock"), 18, null, FontWeights.SemiBold, new Thickness(0, 0, 0, 12), true));
        if (errors.Count == 0) p.Children.Add(Txt("No mistakes are linked to this mock yet.", 13, Ui.Muted));
        foreach (var e in errors)
        {
            var b = new Border { Style = (Style)FindResource("Card"), CornerRadius = new CornerRadius(14), Padding = new Thickness(14, 11, 14, 11), Margin = new Thickness(0, 0, 0, 8) };
            var s = new StackPanel();
            s.Children.Add(Txt(S(e, "Chapter", "Unspecified chapter"), 13, null, FontWeights.SemiBold));
            s.Children.Add(Txt($"{S(e, "Subject")} · {S(e, "Mistake type")}", 11, Ui.Subject(S(e, "Subject")), null, new Thickness(0, 2, 0, 0)));
            if (S(e, "What went wrong") != "") s.Children.Add(Txt(S(e, "What went wrong"), 12, Ui.Muted, null, new Thickness(0, 6, 0, 0), true));
            b.Child = s;
            p.Children.Add(b);
        }
        var scroll = new ScrollViewer { VerticalScrollBarVisibility = ScrollBarVisibility.Auto, HorizontalScrollBarVisibility = ScrollBarVisibility.Disabled, Content = p };
        GlassWindow("Linked mistakes", 480, 460, scroll, null, true).ShowDialog();
    }

    async Task AttachPdf(JsonObject mock, string kind)
    {
        var picker = new OpenFileDialog { Title = "Choose a PDF", Filter = "PDF files (*.pdf)|*.pdf", CheckFileExists = true };
        if (picker.ShowDialog(this) != true) return;
        var info = new FileInfo(picker.FileName);
        if (info.Length > 10 * 1024 * 1024) { Notice(this, "PDFs must be 10 MB or smaller."); return; }
        if (Session is null && kind == "answer") { Notice(this, "Sign in to store an answer sheet in cloud storage."); return; }
        byte[] bytes = await File.ReadAllBytesAsync(picker.FileName);
        var prop = kind + "Pdf";
        var localDir = Path.Combine(DataFolder, "papers");
        Directory.CreateDirectory(localDir);
        var localPath = Path.Combine(localDir, S(mock, "id") + "-" + kind + ".pdf");
        await File.WriteAllBytesAsync(localPath, bytes);
        JsonObject meta = new() { ["name"] = info.Name, ["size"] = info.Length };
        if (Session is not null)
        {
            try
            {
                string path = S(Obj(Session["user"]), "id") + "/" + S(mock, "id") + "/" + kind + ".pdf";
                var token = await AccessToken();
                using var request = new HttpRequestMessage(HttpMethod.Post, Api + "/storage/v1/object/jee-mock-pdfs/" + string.Join("/", path.Split('/').Select(Uri.EscapeDataString)));
                request.Headers.Add("apikey", PublishableKey);
                request.Headers.Authorization = new AuthenticationHeaderValue("Bearer", token);
                request.Headers.TryAddWithoutValidation("x-upsert", "true");
                request.Content = new ByteArrayContent(bytes);
                request.Content.Headers.ContentType = new MediaTypeHeaderValue("application/pdf");
                using var response = await Http.SendAsync(request);
                var raw = await response.Content.ReadAsStringAsync();
                if (!response.IsSuccessStatusCode) throw new Exception(raw);
                meta["storage"] = "supabase"; meta["path"] = path; meta["localCache"] = localPath;
            }
            catch (Exception ex)
            {
                if (kind != "question") { Notice(this, "Cloud PDF upload failed: " + ex.Message); return; }
                meta["storage"] = "local"; meta["key"] = localPath;
                Notice(this, "Cloud upload failed. The question paper is saved on this PC only.\n" + ex.Message);
            }
        }
        else { meta["storage"] = "local"; meta["key"] = localPath; }
        mock[prop] = meta;
        PersistLocal();
        Toast(kind == "question" ? "Question paper attached" : "Answer sheet attached");
        await SyncCloud();
        MockMenu(mock);
    }

    async Task OpenPdf(JsonObject mock, string kind)
    {
        var meta = Obj(mock[kind + "Pdf"]);
        if (mock[kind + "Pdf"] is null) { Notice(this, "No PDF attached."); return; }
        try
        {
            string path = S(meta, "key", S(meta, "localCache"));
            if (S(meta, "storage") == "supabase")
            {
                var token = await AccessToken();
                var url = Api + "/storage/v1/object/authenticated/jee-mock-pdfs/" + string.Join("/", S(meta, "path").Split('/').Select(Uri.EscapeDataString));
                using var req = new HttpRequestMessage(HttpMethod.Get, url);
                req.Headers.Add("apikey", PublishableKey);
                req.Headers.Authorization = new AuthenticationHeaderValue("Bearer", token);
                using var res = await Http.SendAsync(req);
                res.EnsureSuccessStatusCode();
                path = Path.Combine(Path.GetTempPath(), Guid.NewGuid() + ".pdf");
                await File.WriteAllBytesAsync(path, await res.Content.ReadAsByteArrayAsync());
            }
            if (!File.Exists(path)) throw new Exception("This PC does not have a copy of that local PDF.");
            Process.Start(new ProcessStartInfo(path) { UseShellExecute = true });
        }
        catch (Exception ex) { Notice(this, "Could not open PDF: " + ex.Message); }
    }

    int Linked(string id) => Records("mistakes").Count(x => S(x, "Mock test") == id);
    string MockName(string id) => Records("mocks").FirstOrDefault(x => S(x, "id") == id) is JsonNode m ? S(m, "Test name", "Mock test") : string.IsNullOrEmpty(id) ? "No mock linked" : "Saved mock";

    // ───────────────────────── Delete / backup ─────────────────────────
    void Delete(string key, JsonObject item)
    {
        if (!Ask(this, "This entry will be removed. This can't be undone.", "Delete")) return;
        var a = key == "master" ? Arr(Data["master"]) : Records(key);
        for (int i = a.Count - 1; i >= 0; i--) if (S(a[i], "id") == S(item, "id")) a.RemoveAt(i);
        PersistLocal();
        Render();
        Toast("Deleted");
        _ = SyncCloud();
    }

    void Export_Click(object sender, RoutedEventArgs e)
    {
        var dlg = new SaveFileDialog { FileName = "growjee-backup.json", Filter = "JSON backup (*.json)|*.json" };
        if (dlg.ShowDialog(this) == true) { File.WriteAllText(dlg.FileName, Data.ToJsonString(new JsonSerializerOptions { WriteIndented = true })); Toast("Backup exported"); }
    }

    void Import_Click(object sender, RoutedEventArgs e)
    {
        var dlg = new OpenFileDialog { Filter = "JSON backup (*.json)|*.json" };
        if (dlg.ShowDialog(this) != true) return;
        try
        {
            Data = JsonNode.Parse(File.ReadAllText(dlg.FileName))!.AsObject();
            PersistLocal();
            Render(true);
            Toast("Backup imported");
            _ = SyncCloud();
        }
        catch (Exception ex) { Notice(this, "Could not import backup: " + ex.Message); }
    }
}
