import AVFoundation
import SwiftUI
import UIKit

struct QRScannerView: UIViewControllerRepresentable {
    let onScan: (String) -> Void
    let onCancel: () -> Void

    func makeCoordinator() -> Coordinator {
        Coordinator(onScan: onScan, onCancel: onCancel)
    }

    func makeUIViewController(context: Context) -> UIViewController {
        let controller = UIViewController()
        context.coordinator.controller = controller
        context.coordinator.setupSession()
        return controller
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}

    static func dismantleUIViewController(_ uiViewController: UIViewController, coordinator: Coordinator) {
        coordinator.stopSession()
    }
}

extension QRScannerView {
    final class Coordinator: NSObject, AVCaptureMetadataOutputObjectsDelegate {
        let onScan: (String) -> Void
        let onCancel: () -> Void
        weak var controller: UIViewController?
        private let session = AVCaptureSession()
        private var previewLayer: AVCaptureVideoPreviewLayer?

        init(onScan: @escaping (String) -> Void, onCancel: @escaping () -> Void) {
            self.onScan = onScan
            self.onCancel = onCancel
        }

        func setupSession() {
            DispatchQueue.main.async { [weak self] in
                guard let self, let controller = self.controller else { return }
                self.addCancelButton(to: controller.view)
            }

            switch AVCaptureDevice.authorizationStatus(for: .video) {
            case .authorized:
                configureSession()
            case .notDetermined:
                AVCaptureDevice.requestAccess(for: .video) { [weak self] granted in
                    if granted {
                        DispatchQueue.main.async { self?.configureSession() }
                    }
                }
            case .denied, .restricted:
                showCameraDeniedAlert()
            @unknown default:
                break
            }
        }

        func stopSession() {
            guard session.isRunning else { return }
            session.stopRunning()
        }

        private func configureSession() {
            guard let device = AVCaptureDevice.default(for: .video),
                  let input = try? AVCaptureDeviceInput(device: device)
            else {
                showNoCameraAlert()
                return
            }

            session.beginConfiguration()
            session.addInput(input)

            let output = AVCaptureMetadataOutput()
            guard session.canAddOutput(output) else {
                session.commitConfiguration()
                return
            }
            session.addOutput(output)
            output.setMetadataObjectsDelegate(self, queue: .main)
            output.metadataObjectTypes = [.qr]
            session.commitConfiguration()

            DispatchQueue.global(qos: .userInitiated).async { [weak self] in
                self?.session.startRunning()
            }

            DispatchQueue.main.async { [weak self] in
                guard let self, let controller = self.controller else { return }
                let preview = AVCaptureVideoPreviewLayer(session: self.session)
                preview.videoGravity = .resizeAspectFill
                self.previewLayer = preview

                let previewView = PreviewView(frame: controller.view.bounds)
                previewView.previewLayer = preview
                previewView.autoresizingMask = [.flexibleWidth, .flexibleHeight]
                controller.view.addSubview(previewView)
            }
        }

        private func addCancelButton(to view: UIView) {
            let cancelButton = UIButton(type: .close)
            cancelButton.translatesAutoresizingMaskIntoConstraints = false
            cancelButton.addTarget(self, action: #selector(didTapCancel), for: .touchUpInside)
            cancelButton.accessibilityLabel = "Cancel scanning"
            view.addSubview(cancelButton)
            NSLayoutConstraint.activate([
                cancelButton.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 16),
                cancelButton.trailingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.trailingAnchor, constant: -16),
                cancelButton.widthAnchor.constraint(equalToConstant: 28),
                cancelButton.heightAnchor.constraint(equalToConstant: 28),
            ])
        }

        private func showNoCameraAlert() {
            DispatchQueue.main.async { [weak self] in
                guard let self, let controller = self.controller else { return }
                let label = UILabel()
                label.text = "No camera available.\nA physical device is required for QR scanning."
                label.textAlignment = .center
                label.numberOfLines = 0
                label.textColor = .secondaryLabel
                label.font = .preferredFont(forTextStyle: .body)
                label.translatesAutoresizingMaskIntoConstraints = false
                controller.view.addSubview(label)
                NSLayoutConstraint.activate([
                    label.centerXAnchor.constraint(equalTo: controller.view.centerXAnchor),
                    label.centerYAnchor.constraint(equalTo: controller.view.centerYAnchor),
                    label.leadingAnchor.constraint(greaterThanOrEqualTo: controller.view.readableContentGuide.leadingAnchor),
                    label.trailingAnchor.constraint(lessThanOrEqualTo: controller.view.readableContentGuide.trailingAnchor),
                ])
            }
        }

        private func showCameraDeniedAlert() {
            DispatchQueue.main.async { [weak self] in
                guard let self, let controller = self.controller else { return }
                let alert = UIAlertController(
                    title: "Camera access required",
                    message: "Please enable camera access in Settings to scan QR codes.",
                    preferredStyle: .alert
                )
                alert.addAction(UIAlertAction(title: "Cancel", style: .cancel) { [weak self] _ in
                    self?.onCancel()
                })
                alert.addAction(UIAlertAction(title: "Settings", style: .default) { _ in
                    if let url = URL(string: UIApplication.openSettingsURLString) {
                        UIApplication.shared.open(url)
                    }
                })
                controller.present(alert, animated: true)
            }
        }

        @objc private func didTapCancel() {
            onCancel()
        }

        // MARK: - PreviewView

    /// Thin UIView that holds the `AVCaptureVideoPreviewLayer` and keeps
    /// its frame in sync with the view's bounds via `layoutSubviews`.
    private final class PreviewView: UIView {
        var previewLayer: AVCaptureVideoPreviewLayer? {
            get { layer.sublayers?.first as? AVCaptureVideoPreviewLayer }
            set {
                layer.sublayers?.forEach { $0.removeFromSuperlayer() }
                if let newValue { layer.addSublayer(newValue) }
            }
        }

        override func layoutSubviews() {
            super.layoutSubviews()
            previewLayer?.frame = bounds
        }
    }

    // MARK: - AVCaptureMetadataOutputObjectsDelegate

        func metadataOutput(_ output: AVCaptureMetadataOutput, didOutput metadataObjects: [AVMetadataObject], from connection: AVCaptureConnection) {
            guard let object = metadataObjects.first as? AVMetadataMachineReadableCodeObject,
                  let value = object.stringValue
            else { return }

            AudioServicesPlaySystemSound(SystemSoundID(kSystemSoundID_Vibrate))
            stopSession()
            onScan(value)
        }
    }
}
