---
name: CitizenEye Design System
version: 0.1.0
description: Calm civic mobile UI for following French parliamentary activity.
tokens:
  color:
    background:
      value: "#F7F3EA"
      type: color
    surface:
      value: "#FFFCF5"
      type: color
    ink:
      value: "#102A43"
      type: color
    muted:
      value: "#52606D"
      type: color
    civic-blue:
      value: "#1E4ED8"
      type: color
    assembly-gold:
      value: "#D6A84F"
      type: color
    vote-for:
      value: "#167A3A"
      type: color
    vote-against:
      value: "#B42318"
      type: color
    vote-abstain:
      value: "#996A13"
      type: color
  spacing:
    xs:
      value: "4px"
      type: dimension
    sm:
      value: "8px"
      type: dimension
    md:
      value: "16px"
      type: dimension
    lg:
      value: "24px"
      type: dimension
    xl:
      value: "32px"
      type: dimension
  radius:
    card:
      value: "22px"
      type: dimension
    sheet:
      value: "28px"
      type: dimension
  typography:
    display:
      value:
        fontFamily: system-ui
        fontSize: "34px"
        lineHeight: "38px"
        fontWeight: 700
      type: typography
    title:
      value:
        fontFamily: system-ui
        fontSize: "24px"
        lineHeight: "30px"
        fontWeight: 700
      type: typography
    body:
      value:
        fontFamily: system-ui
        fontSize: "16px"
        lineHeight: "23px"
        fontWeight: 400
      type: typography
---

# CitizenEye Design System

## Personality

Clear, civic, calm. The app should feel like a trustworthy public-service companion, not a partisan media feed.

## Product principles

- Explain parliamentary activity in plain French.
- Cite sources and dates wherever production data is used.
- Make uncertainty visible: postal-code ambiguity, non-voting, missing data and stale imports must be explicit.
- Do not use outrage-first ranking. Prioritize recency, civic importance and user relevance.

## Mobile UX rules

- First session must work without account creation.
- Postal-code/city form stays one-handed and under 10 seconds once data is cached.
- Geolocation may autocomplete the postal code/city and show the inferred commune/département/circonscription, but must not auto-validate; the user can edit or confirm.
- When GPS latitude/longitude is available, resolve the point against official/public circonscription boundary geometry before falling back to department-level candidate selection. Still require confirmation because the location may be stale, coarse, or not the user’s home.
- CitizenEye is French-only: every user-facing title, label, empty state, error message, explanation, CTA, and detail-screen string must be in French.
- Use live public data, not seeded demo content: geo.api.gouv.fr for communes and Assemblée nationale Open Data for deputies/votes.
- Each vote card should include a small neutral concern chip derived from the scrutin title: Amendement (modifie une partie précise du texte), Article (vote sur une section du texte), Texte complet (adoption/rejet à une étape parlementaire), Motion (procédure/censure), Budget (vote budgétaire, souvent par partie), or Résolution (position politique de l’Assemblée). Keep these chips visually neutral so they aid comprehension without implying priority or severity.
- Each vote must open a “Texte et détail du vote” screen that keeps official sources separate from external resources, explains the exact effect of Pour/Contre/Abstention/Non-votant in the context of the subject type, and shows clean empty states for parent text, amendment/article/motion details, group comparison, and external resources when not available.
- Vote-detail enrichment should use only official deterministic data: `Scrutins.json.zip` object/dossier/session fields and `Dossiers_Legislatifs.json.zip` parent-text/procedure/step metadata. Amendment/article/motion V1 details may be extracted from official scrutin titles/objects when dedicated amendment/debate datasets are unavailable; keep missing fields null rather than inventing content.
- Cache deputies and votes locally on-device; refresh public imports once per day, and clearly tolerate stale cache if public sources are temporarily unavailable.
- If postal code or city is ambiguous, ask for user selection or more precise address; never invent a single député.
- Tap targets must be at least 44px.
- Every vote card shows: title, result, député position, short summary and source path in detail.
- A député profile/statistics page may compute short-term stats on-device from the current-legislature public vote set, but must label the denominator and avoid implying physical attendance.
- Long vote feeds should render progressively rather than dumping the whole legislature list onto the screen at once.
- Use color for vote position, but pair it with text labels for accessibility.

## Component rules

### Onboarding card

Use a warm background, large direct promise, and one postal-code input. Avoid long political explanations before value is shown.

### Representative card

Show portrait, name, group, constituency and a concise profile/statistics affordance. Avoid turning the card into a biography dump or repeating generic source copy.

### Vote card

Use a civic-document feel: title first, result second, user-relevant deputy position clearly labeled.

## Data states

- Loading: “Recherche de votre circonscription…” with source hint.
- Ambiguous postal code: “Ce code postal couvre plusieurs circonscriptions” and ask for commune/street.
- Empty votes: explain that no recent public scrutin is available from the current data import.
- Error: show recoverable retry and source/import status.

## Accessibility

- Contrast should target WCAG AA.
- Color-coded vote status must always include text.
- Use plain French labels, not internal Assembly data names.
- Avoid aggressive notification language.
