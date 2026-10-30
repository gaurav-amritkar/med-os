# MedOS theme

The `:root` block at the top of `src/index.css` is the shared theme configuration. Change these tokens to adjust the application without editing each screen:

- **Surfaces and text:** `--canvas`, `--surface`, `--surface-inset`, `--sunken`, `--ink`, `--ink-muted`, and `--ink-faint`.
- **Navigation and brand:** `--frame`, `--frame-hover`, `--frame-ink`, `--frame-muted`, `--nav-active`, `--nav-accent`, `--brand-surface`, and `--brand-ink`.
- **Actions and clinical states:** `--action`, `--action-hover`, `--accent-soft`, and the `--critical`, `--urgent`, `--normal`, and `--info` families. Status colors must remain distinguishable and accompanied by text.
- **Shape and density:** `--r-sm`, `--r-md`, `--s1` through `--s10`, `--rail-width`, `--topbar-height`, and `--target`.
- **Typography and elevation:** `--font-sans`, `--font-mono`, `--t-*`, and `--shadow-*`. Font files are imported in `src/main.jsx` and self-hosted.

The shared stylesheet defines the appearance of cards, navigation, tables, forms, buttons, badges, modals, and list controls using these tokens. Some older screens still use inline layout spacing and sizes; theme colors use the shared tokens. After changing colors, run `npm run check:contrast` from `frontend` and review representative screens, including focus, errors, mobile, and high-contrast mode.
