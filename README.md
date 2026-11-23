# Shaxmat — Nokia uchun kuchli shaxmat boti

Java ME ilovasi (CLDC 1.1 / MIDP 2.0). Nokia C2-01 va boshqa Series 40 telefonlarda ishlashi kerak. Android'da J2ME Loader orqali ham ochiladi.

## O'rnatish

1. `Shaxmat.jar` faylini Redmi'ga yuklab ol.
2. Redmi'da faylni tanla: **Ulashish → Bluetooth → Nokia**.
3. Nokia'da kelgan xabarni och va o'rnatishni tasdiqla. Ilova **Ilovalar** yoki **O'yinlar** papkasida paydo bo'ladi.
4. Ilova internet so'ramaydi va hech qanday ruxsat talab qilmaydi.

## Boshqaruv

| Tugma | Vazifa |
|---|---|
| 2 4 6 8 / strelkalar | Kursorni yurgizish |
| 1 3 7 9 | Diagonal bo'ylab yurgizish |
| 5 / markaziy tugma | Donani tanlash va yurish |
| * | Yurishni qaytarish |
| # | Taxtani aylantirish |
| 0 | Maslahat |
| 5 (bot o'ylayotganda) | Bot hozir yursin |
| Chap yoki o'ng soft-tugma | Menyu |

O'yin har yurishdan keyin avtomatik saqlanadi.

### Raqam bilan tez kiritish

Menyu → Kiritish: raqamlar. Ustun (a=1 … h=8) va qator raqamini tering:
e2-e4 = **5 2 5 4**, g1-f3 = **7 1 6 3**. 9 — tozalash (bot o'ylayotganda — "hozir yursin").

### Blits soati

Menyu → Blits soati: 1, 3, 5, 10 yoki 15 daqiqa. Bot to'liq kuchda o'ynaydi va har yurishga qolgan vaqtga
qarab o'ylaydi. Yurishni boshqa taxtaga ko'chirish uchun har safar 4 soniya hisobga olinadi; 5 daqiqada
60 yurishlik o'yinga ham vaqt yetadi. O'ng pastda taxminiy qolgan vaqt ko'rinadi.

## Darajalar

Bot qancha ko'p o'ylasa, shuncha kuchli o'ynaydi. Reytinglar taxminiy, telefon tezligiga bog'liq.

| Daraja | O'ylash vaqti (gacha) | Taxminiy reyting |
|---|---|---|
| Yangi boshlovchi | 0.3 s | ~500 |
| Havaskor | 0.5 s | ~800 |
| O'rta | 1 s | ~1100 |
| Tez | 1 s | ~1450 |
| **Kuchli** (standart) | 3 s | ~1650 |
| Usta | 6 s | ~1850 |
| **Maksimal** | 12 s | ~2000 |

Bot odatda ko'rsatilgan vaqtning yarmidan kamrog'ini ishlatadi. Har bir keyingi daraja oldingisidan aniq kuchli ekani bot-bot o'yinlarida tekshirildi. "Raqib vaqtida o'ylash" funksiyasi "Tez" va undan yuqori darajalarda ishlaydi.

## Dvigatel

O'zimiz yozgan dvigatel, tashqi kod ishlatilmagan:

- Qidiruv: alpha-beta, PVS, iterativ chuqurlashtirish, aspiration oynalar.
- 1.1 versiya: SEE (almashinuv hisobi) bilan yurish tartibi va kesish, counter-move, logarifmik LMR, razoring,
  baholash keshi, dona ro'yxatlari (tezroq yurish generatori va baholash).
- Transpozitsiya jadvali (32–64 ming yozuv).
- Kesish usullari: null-move, LMR, futility va late move pruning.
- Yurishlar tartibi: killer va history evristikalari.
- Shoh holatida chuqurlashtirish va quiescence qidiruvi.
- Baholash: PeSTO jadvallari, o'tib ketgan piyodalar, piyoda tuzilishi, ochiq liniyadagi ruh, shoh himoyasi.
- 35 ta debyut liniyasidan iborat kitob.

## STATUS

**VERIFIED**
- 1.1 dvigateli eski 1.0 ga qarshi: bir xil tugun soni — 60 o'yinda 61,7%; sekin (interpretator) rejimda bir xil vaqt — 60 o'yinda +35 =11 −14 (67,5%, taxminan +127 Elo).
- Baholash refaktoringi: 46 684 tasodifiy pozitsiyada eski baholash bilan 0 farq.
- Perft: 6 ta standart pozitsiyada natijalar to'g'ri.
- Yurish generatori va SAN yozuvi python-chess bilan solishtirildi: 43 622 pozitsiya, 1,3 mln yurish, 0 ta farq.
- 7 ta taktik masala yechildi.
- Darajalar ketma-ketligi: qo'shni darajalar o'zaro 30 tadan o'yin o'ynadi, har safar yuqori daraja aniq yutdi.
- O'zaro o'ynalgan o'yinlar python-chess bilan tekshirildi: hamma yurish qonuniy, natijalar to'g'ri.
- Class fayllar faqat CLDC 1.1 va MIDP 2.0 API'laridan foydalanadi (avtomatik tekshiruv).
- Class versiyasi 47, StackMap preverify qilingan. Kengaytirilgan freymlar JVM'ning qat'iy tekshiruvchisidan o'tdi.
- MIDP emulyatorida ilovaning o'zi sinaldi (52 ta test): tugmalar, sensor, menyular, piyodaning aylanishi, mot, qaytarish, maslahat, saqlash va qayta tiklash, 5 xil ekran o'lchami.

**IMPLEMENTED-UNVERIFIED**
- Haqiqiy Nokia C2-01'da ishga tushirib ko'rilmagan.
- Telefondagi tezlik va shunga ko'ra o'yin kuchi noma'lum. Kompyuterda dvigatel 3 soniyada 13-chuqurlikka yetadi. Telefon taxminan 20–60 baravar sekinroq bo'ladi.

**NOT-IMPLEMENTED**
- O'yinni PGN'ga eksport qilish.
- Vaqt nazorati (shaxmat soati).

## Manbadan yig'ish

Faqat JDK va python3 kerak:

```
./build.sh                 # build/dist/Shaxmat.jar va Shaxmat.jad
```

Testlar `test/` papkasida. `tools/Preverify.java` — o'zimiz yozgan Java ME preverifier.
