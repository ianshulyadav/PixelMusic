import os
import subprocess

def main():
    bot_token    = os.environ['TELEGRAM_BOT_TOKEN']
    chat_id      = os.environ['TELEGRAM_CHAT_ID']
    thread_id    = os.environ.get('TELEGRAM_THREAD_ID', '')
    version      = os.environ['VERSION_NAME']
    changelog    = os.environ['CHANGELOG']
    commit_sha   = os.environ['COMMIT_SHA']
    release_url  = os.environ['RELEASE_URL']
    commit_url   = os.environ['COMMIT_URL']

    caption = (
        f"<b>📱 PixelMusic v{version} — Release</b>\n\n"
        f"<b>Changes:</b>\n{changelog}\n\n"
        f"<b>Commit:</b> <a href='{commit_url}'>{commit_sha[:7]}</a>\n"
        f"<b>Release:</b> <a href='{release_url}'>GitHub Release ↗</a>\n\n"
        f"🚀 All APKs attached below."
    )

    apks = [
        ("app/build/outputs/apk/release/app-universal-release.apk",   caption),
        ("app/build/outputs/apk/release/app-arm64-v8a-release.apk",   "<code>arm64-v8a</code> — Modern phones (Pixel, Samsung, OnePlus…)"),
        ("app/build/outputs/apk/release/app-armeabi-v7a-release.apk", "<code>armeabi-v7a</code> — Older / budget ARM phones"),
        ("app/build/outputs/apk/release/app-x86_64-release.apk",      "<code>x86_64</code> — Emulators &amp; Chromebooks"),
    ]

    url = f"https://api.telegram.org/bot{bot_token}/sendDocument"

    for apk_path, cap in apks:
        args = [
            "curl", "-s",
            "-F", f"document=@{apk_path}",
            "--form-string", f"chat_id={chat_id}",
            "--form-string", "parse_mode=HTML",
            "--form-string", f"caption={cap}",
        ]
        if thread_id:
            args += ["--form-string", f"message_thread_id={thread_id}"]
        args.append(url)
        
        print(f"Sending {apk_path}...")
        result = subprocess.run(args, capture_output=True, text=True)
        print(f"Sent {apk_path}. Output: {result.stdout}")
        if result.stderr:
            print(f"Error output: {result.stderr}")

if __name__ == '__main__':
    main()
