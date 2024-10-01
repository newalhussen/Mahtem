import type { Metadata, Viewport } from "next";
import { Archivo, Mrs_Saint_Delafield, Noto_Sans_Ethiopic } from "next/font/google";
import "./globals.css";

const archivo = Archivo({ subsets: ["latin"], weight: ["400", "600", "800"], variable: "--font-archivo", display: "swap" });
const ethiopic = Noto_Sans_Ethiopic({ subsets: ["ethiopic"], weight: ["400", "600", "700"], variable: "--font-ethiopic", display: "swap" });
const script = Mrs_Saint_Delafield({ subsets: ["latin"], weight: "400", variable: "--font-script", display: "swap" });

export const metadata: Metadata = {
  title: { default: "Mahtem · Credentials that prove themselves", template: "%s · Mahtem" },
  description:
    "Mahtem lets institutions issue digitally sealed credentials and lets anyone verify them in seconds, with no account or app.",
};

export const viewport: Viewport = { width: "device-width", initialScale: 1, themeColor: "#7c1405" };

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html lang="en" className={`${archivo.variable} ${ethiopic.variable} ${script.variable}`}>
      <body>{children}</body>
    </html>
  );
}
