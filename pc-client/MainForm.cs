using System;
using System.Diagnostics;
using System.Drawing;
using System.IO;
using System.Net.Http;
using System.Text.Json;
using System.Threading.Tasks;
using System.Windows.Forms;
using Microsoft.Web.WebView2.Core;
using Microsoft.Web.WebView2.WinForms;

namespace ShowHubPC;

public partial class MainForm : Form
{
    private const int CURRENT_VERSION_CODE = 126;
    private const string CURRENT_VERSION_NAME = "2.8.67";
    private const string DEFAULT_REMOTE_URL = "https://showhub-server.onrender.com";
    private const string LOCAL_URL = "http://localhost:8000";

    private WebView2 webView;
    private bool isFullScreen = false;
    private FormWindowState previousWindowState = FormWindowState.Normal;
    private FormBorderStyle previousBorderStyle = FormBorderStyle.Sizable;
    private string activeServerUrl = DEFAULT_REMOTE_URL;

    public MainForm()
    {
        InitializeComponent();
        InitializeAsync();
    }

    private void InitializeComponent()
    {
        Text = $"ShowHub Media Center (v{CURRENT_VERSION_NAME})";
        Size = new Size(1280, 800);
        MinimumSize = new Size(960, 600);
        StartPosition = FormStartPosition.CenterScreen;
        BackColor = Color.FromArgb(12, 13, 20);

        string iconPath = Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "app.ico");
        if (File.Exists(iconPath))
        {
            try
            {
                Icon = new Icon(iconPath);
            }
            catch { }
        }

        webView = new WebView2
        {
            Dock = DockStyle.Fill,
            BackColor = Color.FromArgb(12, 13, 20)
        };
        Controls.Add(webView);

        KeyPreview = true;
        KeyDown += MainForm_KeyDown;
    }

    private async void InitializeAsync()
    {
        LoadServerConfig();
        await InitWebView();
        _ = CheckUpdatesAsync();
    }

    private void LoadServerConfig()
    {
        string cfgPath = Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "showhub_config.json");
        if (File.Exists(cfgPath))
        {
            try
            {
                string json = File.ReadAllText(cfgPath);
                using var doc = JsonDocument.Parse(json);
                if (doc.RootElement.TryGetProperty("server_url", out var urlEl))
                {
                    string? customUrl = urlEl.GetString();
                    if (!string.IsNullOrWhiteSpace(customUrl))
                    {
                        activeServerUrl = customUrl.Trim().TrimEnd('/');
                        return;
                    }
                }
            }
            catch { }
        }

        // Try local server ping quickly
        try
        {
            using var client = new HttpClient { Timeout = TimeSpan.FromMilliseconds(500) };
            var res = client.GetAsync(LOCAL_URL + "/api/health").GetAwaiter().GetResult();
            if (res.IsSuccessStatusCode)
            {
                activeServerUrl = LOCAL_URL;
                return;
            }
        }
        catch { }

        activeServerUrl = DEFAULT_REMOTE_URL;
    }

    private async Task InitWebView()
    {
        try
        {
            string userDataDir = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "ShowHubPC");
            var env = await CoreWebView2Environment.CreateAsync(null, userDataDir);
            await webView.EnsureCoreWebView2Async(env);

            webView.CoreWebView2.Settings.IsStatusBarEnabled = false;
            webView.CoreWebView2.Settings.AreDevToolsEnabled = true;
            webView.CoreWebView2.Settings.IsZoomControlEnabled = false;

            // Handle hotkeys inside WebView2
            webView.CoreWebView2.NavigationCompleted += (s, e) =>
            {
                webView.CoreWebView2.ExecuteScriptAsync(@"
                    window.addEventListener('keydown', (e) => {
                        if (e.key === 'F11') {
                            e.preventDefault();
                            window.chrome.webview.postMessage('toggle_fullscreen');
                        }
                    });
                ");
            };

            webView.CoreWebView2.WebMessageReceived += (s, e) =>
            {
                string msg = e.TryGetWebMessageAsString();
                if (msg == "toggle_fullscreen")
                {
                    ToggleFullScreen();
                }
            };

            webView.Source = new Uri(activeServerUrl);
        }
        catch (Exception ex)
        {
            MessageBox.Show(
                $"Не удалось инициализировать WebView2 Runtime.\n\nПожалуйста, убедитесь, что установлен Microsoft Edge WebView2 Evergreen Runtime.\n\nОшибка: {ex.Message}",
                "ShowHub PC Error",
                MessageBoxButtons.OK,
                MessageBoxIcon.Error
            );
        }
    }

    private void MainForm_KeyDown(object? sender, KeyEventArgs e)
    {
        if (e.KeyCode == Keys.F11 || (e.Alt && e.KeyCode == Keys.Enter))
        {
            ToggleFullScreen();
            e.Handled = true;
        }
        else if (e.KeyCode == Keys.Escape && isFullScreen)
        {
            ToggleFullScreen();
            e.Handled = true;
        }
    }

    private void ToggleFullScreen()
    {
        if (!isFullScreen)
        {
            previousWindowState = WindowState;
            previousBorderStyle = FormBorderStyle;
            FormBorderStyle = FormBorderStyle.None;
            WindowState = FormWindowState.Normal;
            WindowState = FormWindowState.Maximized;
            isFullScreen = true;
        }
        else
        {
            FormBorderStyle = previousBorderStyle;
            WindowState = previousWindowState;
            isFullScreen = false;
        }
    }

    private async Task CheckUpdatesAsync()
    {
        await Task.Delay(3000); // Wait 3s after startup
        string[] updateUrls = [
            $"{activeServerUrl}/version.json",
            "https://cdn.jsdelivr.net/gh/danilpromsiz-byte/showhub-server@main/version.json",
            "https://raw.githubusercontent.com/danilpromsiz-byte/showhub-server/main/version.json"
        ];

        using var client = new HttpClient { Timeout = TimeSpan.FromSeconds(5) };
        foreach (var url in updateUrls)
        {
            try
            {
                var resp = await client.GetStringAsync(url);
                using var doc = JsonDocument.Parse(resp);
                var root = doc.RootElement;

                int remoteCode = 0;
                string remoteVersion = CURRENT_VERSION_NAME;
                string downloadUrl = $"{activeServerUrl}/ShowHub-PC.zip";

                if (root.TryGetProperty("pc", out var pcObj))
                {
                    if (pcObj.TryGetProperty("version_code", out var codeEl))
                        remoteCode = codeEl.GetInt32();
                    if (pcObj.TryGetProperty("version_name", out var nameEl))
                        remoteVersion = nameEl.GetString() ?? remoteVersion;
                    if (pcObj.TryGetProperty("download_url", out var dlEl))
                        downloadUrl = dlEl.GetString() ?? downloadUrl;
                }
                else
                {
                    if (root.TryGetProperty("version_code", out var codeEl))
                        remoteCode = codeEl.GetInt32();
                    if (root.TryGetProperty("version_name", out var nameEl))
                        remoteVersion = nameEl.GetString() ?? remoteVersion;
                }

                if (remoteCode > CURRENT_VERSION_CODE)
                {
                    string changelog = root.TryGetProperty("changelog", out var clEl) ? clEl.GetString() ?? "" : "";
                    Invoke(() =>
                    {
                        var result = MessageBox.Show(
                            this,
                            $"Доступна новая версия ShowHub PC v{remoteVersion}!\n\nЧто нового:\n{changelog}\n\nОткрыть страницу загрузки обновления?",
                            "Обновление ShowHub PC",
                            MessageBoxButtons.YesNo,
                            MessageBoxIcon.Information
                        );
                        if (result == DialogResult.Yes)
                        {
                            try
                            {
                                Process.Start(new ProcessStartInfo
                                {
                                    FileName = downloadUrl,
                                    UseShellExecute = true
                                });
                            }
                            catch { }
                        }
                    });
                    break;
                }
            }
            catch
            {
                // Try next URL
            }
        }
    }
}
