package app.roadstr.core.discovery.lexicon

/**
 * Search vocabulary: Latvian. Written from general knowledge; a native speaker should review it.
 * Latvian marks "in <place>" with a suffix, so place names are found through the trailing-word guess.
 */
internal object LexiconLv {
    const val TEXT = """
c RESTAURANT: restorāns, restorān*, ēstuve
c FAST_FOOD: ātrā ēdināšana, ātrās ēdināšanas, fast food
c CAFE: kafejnīca, kafejnīc*, kafija
c BAR: bārs, bāri, bar
c PUB: pubs, pubi, pub
c SUPERMARKET: lielveikals, lielveikal*, supermarket*
c CONVENIENCE: veikal*
c PHARMACY: aptieka, aptiek*
c FUEL: degvielas uzpildes stacija, uzpildes stacija, degviela*, benzīn*, uzpilde
c CHARGING_STATION: uzlādes stacija, uzlādes punkts, elektroauto uzlāde, uzlāde*, lādētājs
c PARKING: autostāvvieta, autostāvvieta*, stāvvieta*, parking*, stāvlaukums
c HOTEL: viesnīca, viesnīc*, hostel*, motel*, naktsmītn*, viesu nams
c HOSPITAL: slimnīca, slimnīc*, neatliekamā palīdzība, uzņemšanas nodaļa
c ATM: bankomāts, bankomāt*
c BANK: banka, bank*, bankas
c POST_OFFICE: pasts, pasts*, pasta nodaļa
c POLICE: policija, policij*, policijas iecirknis
c CINEMA: kinoteātris, kinoteātr*, kino
c TRAIN_STATION: dzelzceļa stacija, dzelzceļa stacij*, vilcienu stacija, stacija
a VEGAN: vegānisk*, vegan*
a VEGETARIAN: veģetār*, veģetāri
a GLUTEN_FREE: bez glutēna, bezglutēna*
a OPEN_24_7: visu diennakti, 24 stundas, 24 7
u PIZZA: pica*, picērij*, pizza
near_me: man blakus, tuvumā man, manā tuvumā, tuvākais*, šeit tuvumā, apkārtnē
open_now: atvērts tagad, tagad atvērts, atvērts, atvērta, atvērti, strādā tagad, darbojas
near_both: tuvumā, blakus
near: tuvu, pie, netālu no, iepretim
near_dest: tuvu galamērķim, pie galamērķa, netālu no galamērķa, galamērķī
route: pa maršrutu, maršrutā, ceļā, pa ceļu, ceļojuma laikā
filler: un, ar, par, kas, kur, atrast, meklēju, vēlos, vajag, labs, laba, labākais, lēts, ir, vai
"""
}
