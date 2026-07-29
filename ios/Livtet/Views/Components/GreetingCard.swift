import LivtetKit
import LivtetKitFFI
import SwiftUI

/// Time-of-day greeting card drawn from African American and African
/// diaspora authors. Mirrors the `Greeting` block in Android's
/// `DashboardScreen`.
///
/// Renders two text rows:
/// - The quote body (`greeting.text`) — running prose, slightly faded.
/// - An italic attribution (`"\(author) - \(material)"`) — right-aligned.
///
/// The temporal label (`greeting.label` — e.g. "Good morning") is shown
/// in the navigation bar by `DashboardView`, so it's intentionally
/// omitted here to avoid duplicating it on screen.
struct GreetingCard: View {
    let greeting: Greeting

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(greeting.text)
                .font(.livtetBody(size: 14))
                .foregroundStyle(Color("textNormal").opacity(0.8))

            HStack {
                Spacer()
                Text("\(greeting.author) - \(greeting.material)")
                    .font(.livtetBody(size: 12, weight: .regular))
                    .italic()
                    .foregroundStyle(Color("textNormal").opacity(0.5))
            }
        }
        .padding(.horizontal, 4)
        .accessibilityElement(children: .combine)
        .accessibilityLabel("\(greeting.label), \(greeting.text)")
        .accessibilityHint("\(greeting.author) - \(greeting.material)")
    }
}

#if DEBUG
#Preview {
    GreetingCard(greeting: Greeting(
        label: "Good morning",
        text: "The sun rose slowly, lighting the pages of her book.",
        author: "Toni Morrison",
        material: "Beloved",
        period: "Late Morning"
    ))
    .padding()
    .background(Color("surfaceDefault"))
}
#endif
