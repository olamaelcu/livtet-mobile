import Foundation

public enum JigsawSoundEvent {
    case pieceLifted
    case pieceSnapped
    case pieceDropped
    case hintUsed
    case solved
}

public protocol JigsawSoundFX {
    func play(_ event: JigsawSoundEvent)
}

public struct NoOpSoundFX: JigsawSoundFX {
    public init() {}
    public func play(_ event: JigsawSoundEvent) {}
}
