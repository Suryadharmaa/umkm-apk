# Emulator screenshots

These are full-window captures of the actual debug app on the API 28 emulator at 720×1440 px (360 dp wide) and default font scale. The populated screens use a disposable catalog created through the app UI.

- [Beranda with an empty catalog](beranda-kosong.png)
- [Beranda with saved catalog counts](beranda-katalog.png)
- [Kasir with a selected product](kasir-keranjang.png)
- [Draft cart review](keranjang.png)

Visual review checked the supplied reference direction against the emulator captures. Beranda carries the blue header and rounded white surface into a catalog-focused layout. Kasir keeps the total and cart action above the bottom navigation, with visible add targets in each product row. The app uses its own labels and icons. Pixel-comparison baselines for the Compose surfaces are maintained separately under app/src/androidTest/assets/screenshots.
