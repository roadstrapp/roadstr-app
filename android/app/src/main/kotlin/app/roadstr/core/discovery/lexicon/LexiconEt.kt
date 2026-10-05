package app.roadstr.core.discovery.lexicon

/**
 * Search vocabulary: Estonian. Written from general knowledge; a native speaker should review it.
 * Estonian marks "in <place>" with a suffix, so place names are found through the trailing-word guess.
 */
internal object LexiconEt {
    const val TEXT = """
c RESTAURANT: restoran*, söögikoht
c FAST_FOOD: kiirtoit*, kiirsöökla, fast food
c CAFE: kohvik*, kohvikud, kohvimaja
c BAR: baar*
c PUB: pubi*
c SUPERMARKET: supermarket*, toidupood*
c CONVENIENCE: pood
c PHARMACY: apteek*, apteegid
c FUEL: tankla*, bensiinijaam*, kütusejaam, bensiin
c CHARGING_STATION: laadimispunkt*, laadimisjaam*, elektriauto laadija, laadija, laadimine
c PARKING: parkla*, parkimine, parkimiskoht*, parkimismaja
c HOTEL: hotell*, hostel*, külalistemaja, motell*, majutus*
c HOSPITAL: haigla*, kiirabi, erakorralise meditsiini osakond
c ATM: sularahaautomaat*, pangaautomaat*
c BANK: pank*, pangad
c POST_OFFICE: postkontor*, postipunkt
c POLICE: politsei*, politseijaoskond
c CINEMA: kino*, kinos
c TRAIN_STATION: raudteejaam*, rongijaam
a VEGAN: vegan*, veganlik*
a VEGETARIAN: taimetoit*, vegetaar*
a GLUTEN_FREE: gluteenivaba*, ilma gluteenita
a OPEN_24_7: ööpäevaringselt, 24 tundi, 24 7
u PIZZA: pizza, pitsa*, pizzeria*
near_me: minu lähedal, siin lähedal, lähimad, lähim*, mu lähedal, minu ümbruses
open_now: avatud praegu, praegu avatud, avatud, lahti, praegu lahti, avatud nüüd
near_both: lähedal, kõrval, juures, ligidal
near_dest: sihtkoha lähedal, sihtkoha juures, sihtpunkti lähedal
route: marsruudil, marsruudi ääres, teel, tee ääres, sõidu ajal
filler: ja, koos, jaoks, mis, kus, otsi, otsin, tahan, vajan, hea, parim, odav, on, kas
"""
}
