import os
import subprocess
import json

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
        f"<b>APKs included:</b>\n"
        f"• <code>universal</code> — Works on all devices\n"
        f"• <code>arm64-v8a</code> — Modern phones (Pixel, Samsung, OnePlus…)\n"
        f"• <code>armeabi-v7a</code> — Older / budget ARM phones\n"
        f"• <code>x86_64</code> — Emulators &amp; Chromebooks\n\n"
        f"<b>Commit:</b> <a href='{commit_url}'>{commit_sha[:7]}</a>\n"
        f"<b>Release:</b> <a href='{release_url}'>GitHub Release ↗</a>"
    )

    apks = [
        ("app/build/outputs/apk/release/app-universal-release.apk",   caption),
        ("app/build/outputs/apk/release/app-arm64-v8a-release.apk",   "<code>arm64-v8a</code> — Modern phones (Pixel, Samsung, OnePlus…)"),
        ("app/build/outputs/apk/release/app-armeabi-v7a-release.apk", "<code>armeabi-v7a</code> — Older / budget ARM phones"),
        ("app/build/outputs/apk/release/app-x86_64-release.apk",      "<code>x86_64</code> — Emulators &amp; Chromebooks"),
    ]

    media_group = []
    curl_files_args = []

    for i, (apk_path, cap) in enumerate(apks):
        attachment_name = f"apk{i}"
        media_group.append({
            "type": "document",
            "media": f"attach://{attachment_name}",
            "caption": cap,
            "parse_mode": "HTML"
        })
        curl_files_args += ["-F", f"{attachment_name}=@{apk_path}"]

    url = f"https://api.telegram.org/bot{bot_token}/sendMediaGroup"

    args = [
        "curl", "-s",
        "-F", f"chat_id={chat_id}",
        "--form-string", f"media={json.dumps(media_group)}",
    ]
    if thread_id:
        args += ["--form-string", f"message_thread_id={thread_id}"]

    args += curl_files_args
    args.append(url)

    print("Sending all APKs as a media group...")
    result = subprocess.run(args, capture_output=True, text=True)
    print(f"Response: {result.stdout}")
    if result.stderr:
        print(f"Error output: {result.stderr}")

if __name__ == '__main__':
    main()
