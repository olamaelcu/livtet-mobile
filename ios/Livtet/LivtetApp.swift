import Sentry
import SwiftUI
import os
#if canImport(CoreText)
import CoreText
#endif

@main
struct LivtetApp: App {
    init() {
        Self.registerBrandFonts()

        if !BuildConfig.sentryDSN.isEmpty {
            SentrySDK.start { options in
                options.dsn = BuildConfig.sentryDSN
                options.beforeSend = { event in
                    PiiScrubber.scrub(event)
                    return event
                }
            }
        }
    }

    /// UIAppFonts in Info.plist only works reliably for static fonts; for
    /// variable fonts (Geist, Work Sans, JetBrains Mono) iOS sometimes
    /// silently drops the registration. Re-register explicitly so the
    /// PostScript names (WorkSans-Regular, Geist-Regular,
    /// JetBrainsMono-Regular) are always resolvable.
    private static func registerBrandFonts() {
        let fontFiles = ["work_sans", "geist", "jetbrains_mono"]
        let log = Logger(subsystem: "net.olamaelcu.livtet", category: "Fonts")
        for name in fontFiles {
            guard let url = Bundle.main.url(forResource: name, withExtension: "ttf") else {
                log.error("brand font not in bundle: \(name, privacy: .public).ttf")
                continue
            }
            var error: Unmanaged<CFError>?
            let ok = CTFontManagerRegisterFontsForURL(url as CFURL, .process, &error)
            if !ok, let cfErr = error?.takeRetainedValue() {
                let desc = cfErr as Error
                let nsErr = desc as NSError
                log.error("CTFontManagerRegisterFontsForURL failed for \(name, privacy: .public): domain=\(nsErr.domain, privacy: .public) code=\(nsErr.code) desc=\(desc.localizedDescription, privacy: .public)")
            }
        }
        // Log all registered PostScript names containing Work/Geist/JetBrains
        // so we can see whether UIAppFonts actually loaded the fonts and
        // under what PostScript names they're available to SwiftUI.
        let allNames = CTFontManagerCopyAvailablePostScriptNames() as? [String] ?? []
        let interesting = allNames.filter {
            $0.localizedCaseInsensitiveContains("ork") ||
                $0.localizedCaseInsensitiveContains("eist") ||
                $0.localizedCaseInsensitiveContains("etBrains")
        }
        log.error("registered PostScript names (filtered): \(interesting.joined(separator: ", "), privacy: .public)")
    }

    var body: some Scene {
        WindowGroup {
            SplashScreenView()
        }
    }
}
