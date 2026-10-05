package app.roadstr.core.discovery.lexicon

/**
 * Search vocabulary: Finnish. Written from general knowledge; a native speaker should review it.
 * Finnish marks "in <place>" with a suffix, so place names are found through the trailing-word guess.
 */
internal object LexiconFi {
    const val TEXT = """
c RESTAURANT: ravintola, ravintola*
c FAST_FOOD: pikaruoka*, pikaruokaravintola, pikaruoka
c CAFE: kahvila, kahvila*, kahvi
c BAR: baari*
c PUB: pubi*, pubit
c SUPERMARKET: supermarket, supermarket*, ruokakauppa
c CONVENIENCE: lähikauppa, kioski, kauppa
c PHARMACY: apteekki, apteekki*, apteekit
c FUEL: huoltoasema, huoltoasema*, bensa*, tankkaus, polttoaine*
c CHARGING_STATION: latauspiste, latauspiste*, latausasema*, sähköauton lataus, latauspisteet
c PARKING: parkkipaikka, parkki*, pysäköinti*, pysäköintialue, parkkihalli
c HOTEL: hotelli, hotelli*, hostelli*, motelli*, majoitus*, majatalo
c HOSPITAL: sairaala, sairaala*, päivystys*, ensiapu
c ATM: pankkiautomaatti, pankkiautomaatti*, nostoautomaatti
c BANK: pankki, pankki*, pankit
c POST_OFFICE: postitoimisto, postitoimisto*, posti
c POLICE: poliisiasema, poliisi*
c CINEMA: elokuvateatteri, elokuvateatteri*, leffa, leffateatteri
c TRAIN_STATION: rautatieasema, rautatieasema*, juna asema
a VEGAN: vegaani*, vegaaninen
a VEGETARIAN: kasvisruoka*, kasvis*, vegetaari*
a GLUTEN_FREE: gluteeniton*, ilman gluteenia
a OPEN_24_7: 24 tuntia, ympäri vuorokauden, 24 7
u PIZZA: pizza, pizzeria*, pizzat
near_me: lähimmät, lähin, täällä lähellä, lähimpänä
open_now: auki nyt, nyt auki, auki, avoinna, avoinna nyt, nyt avoinna
near_both: lähellä, vieressä, luona, lähistöllä
near_dest: lähellä määränpäätä, määränpään lähellä, perillä
route: reitin varrella, matkalla, reitillä, tien varrella
filler: ja, kanssa, varten, joka, missä, etsi, etsin, haluan, tarvitsen, hyvä, paras, edullinen, halpa, on, onko, jokin
"""
}
