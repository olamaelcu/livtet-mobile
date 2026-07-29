import SwiftUI

struct ProviderErrorCallout: View {
    let error: ProviderErrorInfo
    let onDismiss: () -> Void

    var body: some View {
        HStack(alignment: .top, spacing: 8) {
            Image(systemName: iconName)
                .foregroundStyle(isAuthOrRate ? Color("semanticDangerForeground") : Color("textQuiet"))
            VStack(alignment: .leading, spacing: 4) {
                Text(error.userMessage)
                    .font(.livtetBody(size: 13))
                    .foregroundStyle(Color("textNormal"))
                Button("Dismiss") { onDismiss() }
                    .font(.livtetBody(size: 12, weight: .semibold))
            }
            Spacer()
        }
        .padding(12)
        .background(RoundedRectangle(cornerRadius: LivtetRadius.l)
            .fill(isAuthOrRate ? Color("semanticDangerBackground") : Color("surfaceHighlighted")))
    }

    private var isAuthOrRate: Bool {
        error.category == .needsAuth || error.category == .rateLimited
    }

    private var iconName: String {
        switch error.category {
        case .needsAuth: return "key.fill"
        case .rateLimited: return "clock.fill"
        case .timeout: return "timer"
        case .notFound: return "magnifyingglass"
        case .providerDown: return "exclamationmark.triangle.fill"
        }
    }
}
