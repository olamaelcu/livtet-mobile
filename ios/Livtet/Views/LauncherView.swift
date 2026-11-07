import SwiftUI

/// Barebone launch surface. The client UI is under reconstruction; the FFI
/// boot path still runs from `SplashScreenView`.
struct LauncherView: View {
    var body: some View {
        ZStack {
            Color("surfaceDefault").ignoresSafeArea()

            VStack(spacing: 16) {
                Image("Logo")
                    .resizable()
                    .scaledToFit()
                    .frame(width: 96, height: 96)
                    .accessibilityHidden(true)

                Text("Livtet")
                    .font(.livtetHeading(size: 34))
                    .foregroundStyle(Color.brand)

                Text("Mobile client under reconstruction")
                    .font(.livtetBody())
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
            }
            .padding()
        }
    }
}

#if DEBUG
#Preview {
    LauncherView()
}
#endif
