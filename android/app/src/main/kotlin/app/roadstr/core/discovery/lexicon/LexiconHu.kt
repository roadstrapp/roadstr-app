package app.roadstr.core.discovery.lexicon

/**
 * Search vocabulary: Hungarian. Written from general knowledge; a native speaker should review it.
 * Hungarian marks "in <place>" with a suffix, so place names are found through the trailing-word guess.
 */
internal object LexiconHu {
    const val TEXT = """
c RESTAURANT: étterem*, vendéglő*, étkezde
c FAST_FOOD: gyorsétterem*, gyors kaja, gyorsétkezde, fast food
c CAFE: kávézó*, kávé, kávéház
c BAR: bár, bárok
c PUB: kocsma*, pub, pubok
c SUPERMARKET: szupermarket*, élelmiszerbolt, közért
c PHARMACY: gyógyszertár*, patika*
c FUEL: benzinkút*, töltőállomás, benzin, üzemanyag
c CHARGING_STATION: elektromos töltő, töltőpont, autótöltő, villanyautó töltő
c PARKING: parkoló*, parkolóház, parkolás, parkolóhely
c HOTEL: szálloda*, hotel*, hostel*, panzió*, motel*, szállás
c HOSPITAL: kórház*, sürgősségi, ügyelet
c ATM: bankautomata*, pénzkiadó automata
c BANK: bank*, bankfiók
c POST_OFFICE: posta*, postahivatal
c POLICE: rendőrség*, rendőrőrs
c CINEMA: mozi*, filmszínház
c TRAIN_STATION: vasútállomás*, pályaudvar*, vonatállomás
a VEGAN: vegán*, vegan*
a VEGETARIAN: vegetáriánus*, vega
a GLUTEN_FREE: gluténmentes*, glutenmentes*, gluten mentes
a OPEN_24_7: nonstop, 24 órás, 24 7
u PIZZA: pizza, pizzéria*, pizzázó
near_me: a közelben, a közelemben, közelemben, a környéken, itt a közelben, legközelebbi*, mellettem, körülöttem
open_now: nyitva most, most nyitva, nyitva, nyitva van, jelenleg nyitva
near: közel, szomszédságában
near_after: közelében
near_both: mellett
near_dest: a célnál, úti cél közelében, a célállomás közelében, cél közelében
route: az útvonal mentén, útközben, az úton
filler: a, az, egy, és, keress, keresek, szeretnék, kell, jó, legjobb, olcsó, van, hol, melyik
"""
}
