# EasyReader – Design

Approved 7 November 2026. Mockups: "EasyReader Mockups" canvas on claude.ai (owner's account): https://claude.ai/artifact/L1nauoDoNXxkdQWiZUDisT. The layout was modelled on reference screenshots the owner shared of another Calibre client; EasyReader uses its own name, icon and styling.

## Colours

| Token | Hex | Use |
| --- | --- | --- |
| background | `#151A2B` | App background (dark navy) |
| surface | `#1E2438` | Cards, info panels |
| surfaceHigh | `#262C42` | Search field, inactive chips |
| sheet | `#231D18` | Bottom sheets (filters, sort) |
| sheetChip | `#3A2E22` | Count badges and icon circles in sheets |
| heroCard | `#3A2418` | "Continue reading" card |
| divider | `#2C334C` | Row dividers |
| accent | `#F2A23A` | Amber: buttons, active tabs, progress, links |
| onAccent | `#1A1408` | Text on amber buttons |
| outline | `#8C7A63` | Outlined buttons |
| text | `#EDE6DA` | Primary text |
| textMuted | `#A79F94` | Secondary text |
| success | `#8FD19E` | "Connected" status |

Reader backgrounds (reader only):

| Theme | Background | Text | Secondary text |
| --- | --- | --- | --- |
| White | `#FFFFFF` | `#1C1C1C` | `#5E5E5E` |
| Light cappuccino | `#EFE2CF` | `#3B2A1C` | `#6E5844` |
| Dark | `#121212` | `#D8D2C8` | `#9C958B` |

## Type

- Headings and book titles: **Alegreya** (serif), weights 700–800. Screen titles 28–32 sp, section titles 20–22 sp.
- Everything else: **Alegreya Sans**, 400/500/700. Body 16–18 sp, secondary 13–15 sp.
- Reader text: Alegreya 20 sp default, line height 1.55; user-adjustable.
- Both fonts are free on Google Fonts (SIL Open Font License); bundle them in the app.

## Shape and spacing

- Corner radius: buttons and fields 14 dp, cards 16–20 dp, bottom sheets 26 dp (top), covers 10 dp.
- Touch targets at least 48 dp.
- Phone side margin 16 dp; tablet 28 dp.
- Icons: simple 2 dp stroke line icons.

## Screens

**Home (phone)** — top bar: menu (left), "My Library" (centre), upload (right). "Continue reading" card: cover, title, author, chapter, % and progress bar. Row of: outlined "Filters & shelves" button, sort button, grid/list toggle. Search field. Horizontal cover rows with "See all": Recently added, Favourites, Unread, All books. Covers 112×168 dp with a heart (top right) and an optional amber progress bar underneath. Pull down to sync.

**Filters & shelves (bottom sheet)** — tabs: Filters / My shelves / Smart shelves. Filters: rows with an icon circle, name, count badge and chevron (Authors, Tags, Series, Languages, Publishers, Formats). My shelves: rows with a colour swatch, name and book count, plus a dashed "+ New shelf" button. Smart shelves: a one-line note that they come from Calibre, then rows with name and count.

**Sort (bottom sheet)** — radio list: Date added (with Newest/Oldest first), Last modified, Title, Author, Publisher, Series; Cancel and Apply buttons.

**Book details** — back and favourite in the top bar. Cover 120×180 dp beside title, author (link), star rating, "% read · formats". Buttons: amber "Read" / "Continue reading", plus outlined Add to shelf and Mark as finished. Info panel rows: Series, Language, Publisher, Published, Added, My shelves, then any other custom columns. Tag chips. Description.

**Reader** — top bar: back, book title, "Aa" (reading settings). Chapter heading, serif body text. Footer: thin progress bar, % and page. Background follows the reader theme.

**Settings** — sections: Server (address, Connected status, library, account), Reading (three theme swatches showing "Aa", mark as finished at %, resume last book on launch), Library (progress bar on covers, sync every 10 min, Sync now button with "Last synced" time, remove finished downloads after 7 days).

**Tablet, landscape** — left: title, search, Filters & shelves, Upload; chip row (All books, Continue reading, Recently added, Favourites, Unread); 5-column cover grid with title and author under each. Right: 420 dp details pane for the selected book (amber outline on its cover in the grid).

## App icon

Option A: navy open book (`#151A2B`, thick rounded stroke) on an amber (`#F2A23A`) rounded square. Source: [`assets/icon.svg`](../assets/icon.svg). Use it to generate the adaptive launcher icon (navy book as foreground, amber as background).
