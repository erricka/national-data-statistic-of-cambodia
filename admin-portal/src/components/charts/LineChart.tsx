import { useEffect, useRef } from "react";

export default function LineChart() {
    const ref = useRef<HTMLDivElement>(null);

    useEffect(() => {
        if (!ref.current) return;

        const data = [64, 78, 72, 95, 88, 120, 134, 156];

        ref.current.innerHTML = `
      <svg viewBox="0 0 400 200">
        ${data.map((v, i) => `
          <circle cx="${i * 50 + 20}" cy="${200 - v}" r="4" fill="blue" />
        `).join("")}
      </svg>
    `;
    }, []);

    return <div ref={ref} />;
}