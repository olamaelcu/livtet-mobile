import AVFoundation
import LivtetJigsaw

public final class SystemSoundFX: JigsawSoundFX, ObservableObject {
    private var players: [JigsawSoundEvent: AVAudioPlayer] = [:]

    public init() {
        let soundsDir = Bundle.main.resourceURL?.appendingPathComponent("Sounds")
        let events: [(JigsawSoundEvent, String)] = [
            (.pieceLifted, "piece_lift"),
            (.pieceSnapped, "piece_snap"),
            (.pieceDropped, "piece_drop"),
            (.hintUsed, "hint"),
            (.solved, "solved"),
        ]
        for (event, name) in events {
            if let url = soundsDir?.appendingPathComponent("\(name).caf"),
               let player = try? AVAudioPlayer(contentsOf: url) {
                players[event] = player
                player.prepareToPlay()
            }
        }
    }

    public func play(_ event: JigsawSoundEvent) {
        players[event]?.play()
    }
}
