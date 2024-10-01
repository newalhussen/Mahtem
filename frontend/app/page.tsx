import Link from "next/link";
import { Certificate } from "@/components/mahtem/certificate";
import { Wordmark } from "@/components/mahtem/logo";
import { VerifyIdForm } from "@/components/mahtem/verify-id-form";

const STEPS = [
  { n: "01", who: "Institution", t: "Issue", d: "The registrar uploads graduates or issues one at a time. Each record is signed with the institution’s own key." },
  { n: "02", who: "Graduate", t: "Receive", d: "The graduate gets a print-ready PDF with a QR code and credential ID, plus a link to share online." },
  { n: "03", who: "Employer", t: "Scan", d: "Anyone holding the certificate scans the code with a phone camera. No app, no sign-up." },
  { n: "04", who: "Mahtem", t: "Verify", d: "Signature, revocation and issuer identity are checked live. The answer is Verified, Revoked or Not verified." },
];

const INST = [
  { k: "Bulk issuance", v: "CSV upload with row-by-row validation and a second approver." },
  { k: "Your template", v: "Your crest, Amharic and English names, signatories." },
  { k: "Instant revocation", v: "Withdraw a credential and every copy reflects it at once." },
  { k: "Verification log", v: "See who checked what, when and from where." },
];

const SEC = [
  { tag: "Ed25519", t: "Signed at the source", d: "Every credential is signed with a key that belongs to the issuing institution." },
  { tag: "AES-256", t: "Keys encrypted at rest", d: "Private keys are stored encrypted and are only opened inside the signing service. They are never shown or exported." },
  { tag: "SHA-256", t: "Tamper-evident PDFs", d: "Verifiers can compare a received PDF against the fingerprint recorded at issuance." },
  { tag: "DNS", t: "Domain-verified issuers", d: "Institutions prove they control their domain before they can issue." },
  { tag: "Receipts", t: "Verification receipts", d: "Each check produces a receipt an HR team can attach to a hiring file." },
  { tag: "Privacy", t: "Minimum disclosure", d: "Verifiers see only what is printed on the certificate. No contact data, no transcripts." },
];

const SAMPLE = {
  recipient: "Selamawit Tesfaye Bekele",
  nameAm: "ሰላማዊት ተስፋዬ በቀለ",
  title: "Bachelor of Science in Computer Science",
  details: "With Great Distinction",
  statement: "having fulfilled all requirements prescribed by the University Senate, has been awarded the degree of",
  conferredDate: "15 July 2026",
  issuedDate: "22 July 2026",
  credId: "MHT-AAU-26-K7Q4-8TZ2",
  verifyUrl: "https://verify.mahtem.et/MHT-AAU-26-K7Q4-8TZ2",
  institution: "Addis Ababa University",
  institutionAm: "አዲስ አበባ ዩኒቨርሲቲ",
  department: "College of Natural & Computational Sciences",
  initials: "AAU",
  signatories: [
    { name: "Dr. Meron Abebe", title: "University Registrar" },
    { name: "Prof. Tadesse Worku", title: "President" },
  ],
};

const pad = "clamp(20px,4vw,56px)";

export default function Landing() {
  return (
    <div className="bg-background text-ink">
      <header className="flex flex-wrap items-center gap-x-7 gap-y-3 border-b-2 border-divider" style={{ padding: `16px ${pad}` }}>
        <div className="mr-auto">
          <Wordmark size={30} text={19} />
        </div>
        <nav className="flex flex-wrap gap-6 text-sm" aria-label="Sections">
          {[["#how", "How it works"], ["#inst", "For institutions"], ["#emp", "For employers"], ["#sec", "Security"]].map(([h, l]) => (
            <a key={h} href={h} className="!text-ink no-underline hover:!text-seal">{l}</a>
          ))}
        </nav>
        <div className="flex gap-2">
          <Link className="btn btn-ghost !text-ink" href="/login">Sign in</Link>
          <a className="btn btn-primary" href="#demo">Request a demo</a>
        </div>
      </header>

      <section className="grid border-b-2 border-divider" style={{ gridTemplateColumns: "repeat(auto-fit,minmax(min(100%,440px),1fr))" }}>
        <div className="flex flex-col justify-center gap-[22px]" style={{ padding: `clamp(32px,5vw,64px) ${pad}` }}>
          <div className="flex items-center gap-2.5 text-[13px] text-neutral-800">
            <span className="ethiopic text-[15px] font-semibold text-seal">ማኅተም</span>
            <span className="h-0.5 w-6 bg-divider" />
            <span>Amharic for “seal”</span>
          </div>
          <h1 className="m-0 max-w-[620px] text-balance leading-[1.03] tracking-[-0.03em]" style={{ fontSize: "clamp(36px,4.4vw,56px)" }}>
            Credentials that prove themselves.
          </h1>
          <p className="m-0 max-w-[520px] text-pretty text-[18px] leading-normal text-neutral-800">
            Universities and training institutions issue digitally sealed certificates. Employers scan the QR code with any phone camera and get a clear answer in seconds, with no account or app.
          </p>
          <div className="flex flex-wrap gap-2.5">
            <a className="btn btn-primary !min-h-[46px] !px-[18px] !text-[15px]" href="#demo">Request a demo</a>
            <a className="btn btn-secondary !min-h-[46px] !px-[18px] !text-[15px]" href="#how">See how verification works</a>
          </div>
          <div className="mt-2.5 max-w-[520px] border-t-2 border-divider pt-[18px]">
            <VerifyIdForm id="hid" label="Verify a credential by ID" />
          </div>
        </div>
        <div className="relative flex min-h-[440px] items-center overflow-hidden border-divider bg-surface md:border-l-2" style={{ padding: "clamp(28px,4vw,56px)" }}>
          <div className="max-w-full -rotate-[1.2deg] shadow-lg">
            <Certificate {...SAMPLE} w={500} />
          </div>
          <div className="absolute bottom-6 hidden w-[210px] border border-neutral-300 bg-background shadow-lg sm:block" style={{ right: "clamp(16px,3vw,40px)" }} aria-hidden="true">
            <div className="flex flex-col gap-1.5 bg-ok p-3.5 text-[#f8f4f4]">
              <div className="flex items-center gap-2">
                <div className="grid size-[26px] place-items-center border-[1.5px] border-[#f8f4f4]">
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3"><path d="M20 6 9 17l-5-5" /></svg>
                </div>
                <span className="text-[20px] font-extrabold tracking-[-0.02em]">VERIFIED</span>
              </div>
              <div className="text-[11px] font-semibold">Authentic and in good standing</div>
            </div>
            <div className="flex flex-col gap-1.5 px-3.5 py-3 text-[11px]">
              <div className="text-sm font-extrabold leading-[1.15]">Selamawit Tesfaye Bekele</div>
              <div>BSc Computer Science · AAU</div>
              <div className="mono text-neutral-700">MHT-AAU-26-K7Q4-8TZ2</div>
              <div className="flex items-center gap-1.5 border-t border-divider pt-1.5"><span className="size-2 bg-ok" />Signature valid · Not revoked</div>
            </div>
          </div>
        </div>
      </section>

      <section id="how" className="border-b-2 border-divider">
        <div className="flex flex-wrap items-baseline gap-5" style={{ padding: `40px ${pad} 20px` }}>
          <h2 className="m-0 text-[32px] tracking-[-0.02em]">How it works</h2>
          <span className="text-[15px] text-neutral-800">One sealed record, checked the same way everywhere.</span>
        </div>
        <div className="grid border-t-2 border-divider" style={{ gridTemplateColumns: "repeat(auto-fit,minmax(220px,1fr))" }}>
          {STEPS.map((s) => (
            <div key={s.n} className="flex flex-col gap-2.5 border-r border-divider px-[clamp(20px,2.4vw,32px)] pb-8 pt-6">
              <div className="mono text-[13px] font-semibold text-seal">{s.n}</div>
              <div className="eyebrow !text-xs !text-neutral-700">{s.who}</div>
              <div className="text-[21px] font-extrabold tracking-[-0.01em]">{s.t}</div>
              <div className="text-pretty text-sm leading-[1.55] text-neutral-800">{s.d}</div>
            </div>
          ))}
        </div>
      </section>

      <section className="grid border-b-2 border-divider" style={{ gridTemplateColumns: "repeat(auto-fit,minmax(min(100%,420px),1fr))" }}>
        <div id="inst" className="flex flex-col gap-4 border-divider md:border-r-2" style={{ padding: `40px ${pad}` }}>
          <div className="text-xs font-semibold uppercase tracking-[0.1em] text-seal">For institutions</div>
          <h2 className="m-0 max-w-[460px] text-[28px] tracking-[-0.02em]">Issue a graduating class in one sitting, and stop answering verification letters.</h2>
          <div className="flex flex-col border-t-2 border-divider">
            {INST.map((i) => (
              <div key={i.k} className="grid gap-4 border-b border-divider py-3 text-sm" style={{ gridTemplateColumns: "minmax(0,180px) 1fr" }}>
                <span className="font-semibold">{i.k}</span>
                <span className="text-neutral-800">{i.v}</span>
              </div>
            ))}
          </div>
        </div>
        <div id="emp" className="flex flex-col gap-4" style={{ padding: `40px ${pad}` }}>
          <div className="text-xs font-semibold uppercase tracking-[0.1em] text-seal">For employers</div>
          <h2 className="m-0 max-w-[460px] text-[28px] tracking-[-0.02em]">Point your camera at the certificate. That is the whole process.</h2>
          <div className="grid grid-cols-3 border-t-2 border-divider">
            <div className="flex flex-col gap-1.5 border-r border-divider py-3.5 pr-3"><span className="size-3.5 bg-ok" /><span className="font-extrabold">Verified</span><span className="text-[13px] text-neutral-800">Authentic, signed, not revoked.</span></div>
            <div className="flex flex-col gap-1.5 border-r border-divider px-3 py-3.5"><span className="size-3.5 bg-seal" /><span className="font-extrabold">Revoked</span><span className="text-[13px] text-neutral-800">Withdrawn by the issuer, with the date and reason.</span></div>
            <div className="flex flex-col gap-1.5 py-3.5 pl-3"><span className="size-3.5 bg-neutral-900" /><span className="font-extrabold">Not verified</span><span className="text-[13px] text-neutral-800">Unknown or altered. Do not accept.</span></div>
          </div>
          <Link className="btn btn-secondary self-start" href="/verify/MHT-AAU-26-K7Q4-8TZ2">See a sample verification</Link>
        </div>
      </section>

      <section id="sec" className="border-b-2 border-divider">
        <div style={{ padding: `40px ${pad} 20px` }}>
          <h2 className="m-0 text-[32px] tracking-[-0.02em]">Security you can explain to a Senate committee</h2>
        </div>
        <div className="grid border-t-2 border-divider" style={{ gridTemplateColumns: "repeat(auto-fit,minmax(260px,1fr))" }}>
          {SEC.map((s) => (
            <div key={s.tag} className="flex flex-col gap-1.5 border-b border-r border-divider px-[clamp(20px,2.4vw,32px)] pb-[26px] pt-[22px]">
              <div className="mono text-xs font-semibold text-neutral-700">{s.tag}</div>
              <div className="text-[18px] font-extrabold">{s.t}</div>
              <div className="text-sm leading-[1.55] text-neutral-800">{s.d}</div>
            </div>
          ))}
        </div>
      </section>

      <section id="demo" className="flex flex-col gap-[22px] bg-seal text-[#f8f4f4]" style={{ padding: `clamp(40px,6vw,72px) ${pad}` }}>
        <h2 className="m-0 max-w-[900px] leading-none tracking-[-0.03em]" style={{ fontSize: "clamp(34px,5vw,64px)" }}>Seal your next graduating class.</h2>
        <p className="m-0 max-w-[560px] text-[17px] leading-normal">Onboarding takes about two weeks: domain verification, signing key ceremony, and your certificate template.</p>
        <div className="flex flex-wrap gap-2.5">
          <a className="btn !min-h-[46px] !bg-[#f8f4f4] !px-[18px] !text-[15px] !text-seal" href="mailto:hello@mahtem.et?subject=Mahtem%20demo">Request a demo</a>
          <Link className="btn !min-h-[46px] !border-[rgba(248,244,244,.6)] !px-[18px] !text-[15px] !text-[#f8f4f4]" href="/login">Sign in to the issuer console</Link>
        </div>
      </section>

      <footer className="flex flex-wrap items-center gap-6 text-[13px] text-neutral-800" style={{ padding: `28px ${pad}` }}>
        <span className="font-extrabold text-ink">Mahtem</span>
        <span>Addis Ababa, Ethiopia</span>
        <div className="ml-auto flex flex-wrap gap-5">
          <Link href="/verify">Verify a credential</Link>
          <a href="#sec">Security</a>
        </div>
      </footer>
    </div>
  );
}
