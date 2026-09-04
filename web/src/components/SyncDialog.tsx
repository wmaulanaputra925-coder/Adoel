import { useEffect, useRef, useState } from "react";
import QRCode from "qrcode";
import jsQR from "jsqr";
import { useDoffStore } from "../store/DoffStore";
import { useUiStore } from "../store/UiStore";
import {
  getCustomizedMachinesCount,
  getNextShiftEstimasiEntries,
  prepareHandoverData,
  prepareMasterDbData,
} from "../domain/sync";
import { CameraIcon, ClipboardIcon, CloseIcon, QrCodeScannerIcon, UploadIcon } from "./Icons";

export function SyncDialog({
  onClose,
  initialTab = "kirim",
  isFirstTimeEmpty = false,
}: {
  onClose: () => void;
  initialTab?: "kirim" | "terima";
  isFirstTimeEmpty?: boolean;
}) {
  const { state, importQrSync } = useDoffStore();
  const { showToast } = useUiStore();
  const [tab, setTab] = useState<"kirim" | "terima">(initialTab);
  const [qrType, setQrType] = useState<"HANDOVER" | "MASTER_DB">("HANDOVER");
  const [dbScope, setDbScope] = useState<"CUSTOMIZED_ONLY" | "RANGE_1_30" | "RANGE_31_60" | "ALL">("CUSTOMIZED_ONLY");
  const [qrDataUrl, setQrDataUrl] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);
  const [pastedText, setPastedText] = useState("");
  const [isScanning, setIsScanning] = useState(false);
  const [cameraError, setCameraError] = useState<string | null>(null);

  const videoRef = useRef<HTMLVideoElement | null>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const animFrameIdRef = useRef<number | null>(null);

  const customizedCount = getCustomizedMachinesCount(state);

  // Generate QR Code data URL when in Kirim mode
  useEffect(() => {
    if (tab !== "kirim") return;
    let raw = "";
    if (qrType === "HANDOVER") {
      raw = prepareHandoverData(state);
    } else {
      raw = prepareMasterDbData(state, dbScope);
    }

    QRCode.toDataURL(raw, {
      errorCorrectionLevel: "L",
      width: 300,
      margin: 2,
      color: {
        dark: "#080c14",
        light: "#ffffff",
      },
    })
      .then((url) => setQrDataUrl(url))
      .catch(() => setQrDataUrl(null));
  }, [tab, qrType, dbScope, state]);

  // Clean up camera stream on unmount or tab switch
  const stopCamera = () => {
    if (animFrameIdRef.current) {
      cancelAnimationFrame(animFrameIdRef.current);
      animFrameIdRef.current = null;
    }
    if (streamRef.current) {
      streamRef.current.getTracks().forEach((track) => track.stop());
      streamRef.current = null;
    }
    setIsScanning(false);
  };

  useEffect(() => {
    return () => {
      stopCamera();
    };
  }, []);

  const handleStartCamera = async () => {
    setCameraError(null);
    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        video: { facingMode: "environment" },
      });
      streamRef.current = stream;
      if (videoRef.current) {
        videoRef.current.srcObject = stream;
        await videoRef.current.play();
        setIsScanning(true);
        scanFrame();
      }
    } catch (err: unknown) {
      setCameraError(err instanceof Error ? err.message : "Tidak dapat mengakses kamera");
      setIsScanning(false);
    }
  };

  const handleProcessQrString = (raw: string) => {
    const result = importQrSync(raw.trim());
    if (result.success) {
      showToast(result.message || "Sinkronisasi QR berhasil ✓");
      stopCamera();
      onClose();
    } else {
      showToast("⚠ Format QR Sync tidak valid");
    }
  };

  const handlePasteFromClipboard = async () => {
    try {
      if (navigator.clipboard && navigator.clipboard.readText) {
        const text = await navigator.clipboard.readText();
        if (text && text.trim()) {
          setPastedText(text.trim());
          showToast("Teks ditempel dari clipboard ✓");
          return;
        }
      }
      showToast("Clipboard kosong atau tidak diizinkan browser");
    } catch {
      showToast("Gunakan tempel manual pada kotak teks");
    }
  };

  const scanFrame = () => {
    const video = videoRef.current;
    const canvas = canvasRef.current;
    if (!video || !canvas || video.readyState !== video.HAVE_ENOUGH_DATA) {
      animFrameIdRef.current = requestAnimationFrame(scanFrame);
      return;
    }

    canvas.width = video.videoWidth;
    canvas.height = video.videoHeight;
    const ctx = canvas.getContext("2d", { willReadFrequently: true });
    if (ctx) {
      ctx.drawImage(video, 0, 0, canvas.width, canvas.height);
      const imageData = ctx.getImageData(0, 0, canvas.width, canvas.height);
      const code = jsQR(imageData.data, imageData.width, imageData.height, {
        inversionAttempts: "dontInvert",
      });

      if (code && code.data) {
        handleProcessQrString(code.data);
        return;
      }
    }

    animFrameIdRef.current = requestAnimationFrame(scanFrame);
  };

  const handleFileUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    const reader = new FileReader();
    reader.onload = (event) => {
      const img = new Image();
      img.onload = () => {
        const canvas = document.createElement("canvas");
        canvas.width = img.width;
        canvas.height = img.height;
        const ctx = canvas.getContext("2d", { willReadFrequently: true });
        if (ctx) {
          ctx.drawImage(img, 0, 0);
          const imgData = ctx.getImageData(0, 0, canvas.width, canvas.height);
          const code = jsQR(imgData.data, imgData.width, imgData.height);
          if (code && code.data) {
            handleProcessQrString(code.data);
          } else {
            showToast("⚠ QR Code tidak terdeteksi pada gambar");
          }
        }
      };
      img.src = event.target?.result as string;
    };
    reader.readAsDataURL(file);
  };

  const handleCopyRaw = () => {
    const raw =
      qrType === "HANDOVER"
        ? prepareHandoverData(state)
        : prepareMasterDbData(state, dbScope);
    navigator.clipboard.writeText(raw).then(() => {
      setCopied(true);
      showToast("Data QR disalin ke clipboard ✓");
      setTimeout(() => setCopied(false), 2000);
    });
  };

  return (
    <div className="dialog-backdrop" onClick={() => { stopCamera(); onClose(); }}>
      <div className="dialog" onClick={(e) => e.stopPropagation()} style={{ maxWidth: 440, maxHeight: "90vh", overflowY: "auto" }}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 14 }}>
          <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
            <div
              style={{
                width: 32,
                height: 32,
                borderRadius: 8,
                background: "rgba(6, 182, 212, 0.15)",
                color: "var(--cyan-400)",
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
              }}
            >
              <QrCodeScannerIcon size={18} />
            </div>
            <div>
              <div style={{ fontWeight: 800, fontSize: 17, color: "var(--text-primary)" }}>QR Sync Mesin</div>
              <div style={{ fontSize: 11, color: "var(--text-faint)" }}>Sinkronisasi data mesin & estimasi</div>
            </div>
          </div>
          <button className="icon-btn" onClick={() => { stopCamera(); onClose(); }} aria-label="Tutup">
            <CloseIcon />
          </button>
        </div>

        {isFirstTimeEmpty && (
          <div
            style={{
              padding: "10px 12px",
              borderRadius: 10,
              background: "rgba(6, 182, 212, 0.12)",
              border: "1px solid rgba(6, 182, 212, 0.28)",
              color: "var(--text-primary)",
              fontSize: 12,
              lineHeight: 1.45,
              marginBottom: 14,
            }}
          >
            <div style={{ fontWeight: 700, color: "var(--cyan-400)", marginBottom: 2 }}>
              👋 Selamat Datang di Adoel!
            </div>
            <div>
              Data mesin Anda masih kosong. Pindai kode QR dari rekan kerja/shift lain atau tempel teks data QR untuk sinkronisasi otomatis.
            </div>
          </div>
        )}

        {/* Tab switcher: Kirim vs Terima */}
        <div className="page-toggle" style={{ marginBottom: 16 }}>
          <button
            className={tab === "terima" ? "active" : ""}
            onClick={() => {
              setTab("terima");
            }}
          >
            Terima / Scan
          </button>
          <button
            className={tab === "kirim" ? "active" : ""}
            onClick={() => {
              stopCamera();
              setTab("kirim");
            }}
          >
            Kirim
          </button>
        </div>

        {tab === "kirim" ? (
          <div>
            <div style={{ fontSize: 12, color: "var(--text-faint)", marginBottom: 10 }}>Pilih data yang ingin dikirim:</div>
            <div style={{ display: "flex", gap: 8, marginBottom: 10 }}>
              <button
                className={`chip-btn${qrType === "HANDOVER" ? " active" : ""}`}
                style={{ flex: 1 }}
                onClick={() => setQrType("HANDOVER")}
              >
                Oper Shift ({getNextShiftEstimasiEntries(state).length} Mc)
              </button>
              <button
                className={`chip-btn${qrType === "MASTER_DB" ? " active" : ""}`}
                style={{ flex: 1 }}
                onClick={() => setQrType("MASTER_DB")}
              >
                Daftar Mesin ({customizedCount > 0 ? `${customizedCount} Terisi` : "60 Mc"})
              </button>
            </div>

            {/* Jika mode MASTER_DB, berikan opsi rentang / compact */}
            {qrType === "MASTER_DB" && (
              <div
                style={{
                  display: "flex",
                  gap: 6,
                  marginBottom: 10,
                  background: "var(--bg-elevated-2)",
                  padding: 4,
                  borderRadius: 8,
                }}
              >
                <button
                  className={`chip-btn${dbScope === "CUSTOMIZED_ONLY" ? " active" : ""}`}
                  style={{ flex: 1, fontSize: 11, padding: "5px 4px" }}
                  onClick={() => setDbScope("CUSTOMIZED_ONLY")}
                >
                  Mesin Terisi ({customizedCount || 30})
                </button>
                <button
                  className={`chip-btn${dbScope === "RANGE_1_30" ? " active" : ""}`}
                  style={{ flex: 1, fontSize: 11, padding: "5px 4px" }}
                  onClick={() => setDbScope("RANGE_1_30")}
                >
                  Mc 01–30 (P1)
                </button>
                <button
                  className={`chip-btn${dbScope === "RANGE_31_60" ? " active" : ""}`}
                  style={{ flex: 1, fontSize: 11, padding: "5px 4px" }}
                  onClick={() => setDbScope("RANGE_31_60")}
                >
                  Mc 31–60 (P2)
                </button>
              </div>
            )}

            <div
              style={{
                fontSize: 11,
                color: "var(--text-muted)",
                background: "var(--bg-elevated-2)",
                padding: "8px 10px",
                borderRadius: 8,
                marginBottom: 12,
                lineHeight: 1.4,
              }}
            >
              {qrType === "HANDOVER" ? (
                <span>
                  💡 <strong>Oper Shift Berikutnya:</strong> Hanya membagikan data estimasi untuk shift berikutnya dalam format super ringkas.
                </span>
              ) : (
                <span>
                  💡 <strong>QR Ringkas & Renggang:</strong> Kode QR dioptimalkan agar modul titik besar dan mudah di-scan kamera ponsel. Gunakan <em>P1 / P2</em> jika ingin membagi data mesin menjadi 2 bagian.
                </span>
              )}
            </div>

            <div
              style={{
                background: "#ffffff",
                padding: 12,
                borderRadius: 12,
                display: "flex",
                justifyContent: "center",
                alignItems: "center",
                marginBottom: 16,
                minHeight: 240,
              }}
            >
              {qrDataUrl ? (
                <img
                  src={qrDataUrl}
                  alt="QR Code Sync"
                  style={{ width: 230, height: 230, display: "block", imageRendering: "pixelated" }}
                />
              ) : (
                <div style={{ color: "#333", fontSize: 12 }}>Menyiapkan QR...</div>
              )}
            </div>

            <div style={{ display: "flex", gap: 8 }}>
              <button
                className="confirm"
                style={{ flex: 1, background: "var(--bg-elevated-2)", color: "var(--text-main)", border: "1px solid var(--border-line)" }}
                onClick={handleCopyRaw}
              >
                {copied ? "Tersalin ✓" : "Salin Data Teks"}
              </button>
              <button className="confirm" style={{ flex: 1, background: "var(--cyan-600)" }} onClick={onClose}>
                Selesai
              </button>
            </div>
          </div>
        ) : (
          <div>
            <div style={{ fontSize: 12, color: "var(--text-faint)", marginBottom: 12 }}>
              Arahkan kamera ke kode QR perangkat lain, atau unggah gambar / tempel teks datanya:
            </div>

            {/* Video preview for live scanner */}
            <div
              style={{
                position: "relative",
                width: "100%",
                background: "#000",
                borderRadius: 12,
                overflow: "hidden",
                minHeight: 180,
                display: "flex",
                flexDirection: "column",
                alignItems: "center",
                justifyContent: "center",
                marginBottom: 12,
                border: "1px solid var(--border)",
              }}
            >
              <video
                ref={videoRef}
                playsInline
                muted
                style={{ width: "100%", maxHeight: 220, objectFit: "cover", display: isScanning ? "block" : "none" }}
              />
              <canvas ref={canvasRef} style={{ display: "none" }} />

              {!isScanning && (
                <div style={{ textAlign: "center", padding: 16, width: "100%" }}>
                  {cameraError ? (
                    <div style={{ color: "var(--red-400)", fontSize: 12, marginBottom: 8 }}>⚠ {cameraError}</div>
                  ) : null}
                  <button
                    className="confirm"
                    style={{
                      background: "var(--cyan-600)",
                      display: "inline-flex",
                      alignItems: "center",
                      justifyContent: "center",
                      gap: 6,
                    }}
                    onClick={handleStartCamera}
                  >
                    <CameraIcon size={18} />
                    <span>Buka Kamera Pemindai</span>
                  </button>
                </div>
              )}

              {isScanning && (
                <button
                  className="cancel"
                  style={{
                    position: "absolute",
                    bottom: 8,
                    background: "rgba(0,0,0,0.7)",
                    color: "#fff",
                    padding: "4px 14px",
                    borderRadius: 16,
                    fontSize: 12,
                  }}
                  onClick={stopCamera}
                >
                  Hentikan Kamera
                </button>
              )}
            </div>

            <div style={{ display: "flex", alignItems: "center", gap: 8, margin: "12px 0" }}>
              <div style={{ flex: 1, height: 1, background: "var(--border)" }} />
              <span style={{ fontSize: 11, color: "var(--text-faint)", fontWeight: 700 }}>ATAU</span>
              <div style={{ flex: 1, height: 1, background: "var(--border)" }} />
            </div>

            {/* File upload and text paste */}
            <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
              <label
                className="confirm"
                style={{
                  display: "inline-flex",
                  alignItems: "center",
                  justifyContent: "center",
                  gap: 6,
                  textAlign: "center",
                  background: "var(--bg-elevated-2)",
                  color: "var(--text-primary)",
                  border: "1px solid var(--border)",
                  cursor: "pointer",
                  padding: "10px 16px",
                  borderRadius: 10,
                }}
              >
                <UploadIcon size={16} />
                <span>Pilih Gambar QR dari File</span>
                <input type="file" accept="image/*" style={{ display: "none" }} onChange={handleFileUpload} />
              </label>

              <div>
                <div style={{ fontSize: 11, color: "var(--text-faint)", marginBottom: 4, fontWeight: 600 }}>
                  Masukkan / Tempel Teks QR Sync:
                </div>
                <div style={{ display: "flex", gap: 6 }}>
                  <input
                    className="field-input"
                    placeholder="Tempel data JSON/teks QR di sini..."
                    value={pastedText}
                    onChange={(e) => setPastedText(e.target.value)}
                    style={{ flex: 1 }}
                  />
                  <button
                    type="button"
                    className="icon-btn"
                    title="Tempel dari Clipboard"
                    onClick={handlePasteFromClipboard}
                    style={{ width: 40, height: 40, borderRadius: 8 }}
                  >
                    <ClipboardIcon size={18} />
                  </button>
                  <button
                    className="confirm"
                    style={{ background: "var(--cyan-600)", padding: "0 16px" }}
                    disabled={!pastedText.trim()}
                    onClick={() => handleProcessQrString(pastedText)}
                  >
                    Impor
                  </button>
                </div>
              </div>

              {isFirstTimeEmpty && (
                <div style={{ marginTop: 6, textAlign: "center" }}>
                  <button
                    type="button"
                    onClick={onClose}
                    style={{
                      background: "transparent",
                      border: "none",
                      color: "var(--text-faint)",
                      fontSize: 12,
                      cursor: "pointer",
                      padding: "6px 12px",
                      textDecoration: "underline",
                    }}
                  >
                    Lewati (Atur Manual Nanti)
                  </button>
                </div>
              )}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
