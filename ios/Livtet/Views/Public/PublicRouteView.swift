import SwiftUI

/// Root of the public-permalink sheet: switches on the parsed route and
/// registers the destination used by the links inside each screen.
struct PublicRouteView: View {
    let route: PublicRoute

    var body: some View {
        content
            .navigationTitle(title)
            .navigationBarTitleDisplayMode(.inline)
            .navigationDestination(for: PublicRoute.self) { destination in
                PublicRouteView(route: destination)
            }
            .background(Color("surfaceDefault").ignoresSafeArea())
    }

    @ViewBuilder
    private var content: some View {
        switch route {
        case let .book(did, rkey):
            BookDetailView(did: did, rkey: rkey)
        case let .review(did, rkey):
            ReviewDetailView(did: did, rkey: rkey)
        case let .profile(actor, tab, shelf):
            PublicProfileView(actor: actor, tab: tab, shelf: shelf)
        }
    }

    private var title: String {
        switch route {
        case .book: return "Book"
        case .review: return "Review"
        case .profile: return "Profile"
        }
    }
}
