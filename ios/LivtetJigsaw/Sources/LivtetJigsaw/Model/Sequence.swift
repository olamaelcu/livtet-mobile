public typealias InsertsGenerator = (Int, Int) -> Insert

public final class InsertSequence {
    private let generator: InsertsGenerator
    private var index: Int = 0
    private var previous: Insert?

    public init(_ generator: @escaping InsertsGenerator) {
        self.generator = generator
    }

    public func next() -> Insert {
        let value = generator(index, index)
        previous = value
        index += 1
        return value
    }

    public func previousComplement() -> Insert {
        return previous?.complement ?? .none
    }

    public func current(_ totalCount: Int) -> Insert {
        if index == totalCount {
            return .none
        }
        return generator(index, index)
    }

    public static let fixed: InsertsGenerator = { _, _ in .tab }

    public static let twoAndTwo: InsertsGenerator = { index, _ in
        (index % 4) < 2 ? .tab : .slot
    }
}
