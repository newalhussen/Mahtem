import QRCode from "qrcode";

/** A real, scannable QR code rendered as a single vector path (no image, so it stays sharp at any size). */
export function Qr({ text, fill = "#201e1d", bg = "#fbf9f7" }: { text: string; fill?: string; bg?: string }) {
  const qr = QRCode.create(text, { errorCorrectionLevel: "M" });
  const n = qr.modules.size;
  const q = 2; // quiet zone, in modules
  let d = "";
  for (let y = 0; y < n; y++) {
    for (let x = 0; x < n; x++) {
      if (qr.modules.get(x, y)) d += `M${x + q} ${y + q}h1v1h-1z`;
    }
  }
  const s = n + q * 2;
  return (
    <svg viewBox={`0 0 ${s} ${s}`} width="100%" height="100%" shapeRendering="crispEdges" role="img" aria-label="QR code that opens the Mahtem verification page" style={{ display: "block" }}>
      <rect width={s} height={s} fill={bg} />
      <path d={d} fill={fill} />
    </svg>
  );
}
