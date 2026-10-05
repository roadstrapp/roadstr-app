package app.roadstr.core.discovery.lexicon

/**
 * Search vocabulary: Lithuanian. Written from general knowledge; a native speaker should review it.
 * Lithuanian marks "in <place>" with a suffix, so place names are found through the trailing-word guess.
 */
internal object LexiconLt {
    const val TEXT = """
c RESTAURANT: restoran*, valgykla*
c FAST_FOOD: greitas maistas, greito maisto, fast food
c CAFE: kavinė*, kavinės, kavos
c BAR: bar, baras, barai
c PUB: pub, pubas, alinė
c SUPERMARKET: supermarket*
c CONVENIENCE: parduotuv*
c PHARMACY: vaistin*
c FUEL: degalinė, degalin*, kuro stotis, benzin*, degalai
c CHARGING_STATION: įkrovimo stotelė, įkrovimo stotis, elektromobilių įkrovimas, įkrovimas, įkroviklis
c PARKING: stovėjimo aikštelė, automobilių aikštelė, parkavimas, parkingas
c HOTEL: viešbut*, hostel*, nakvynė*, svečių namai, motel*
c HOSPITAL: ligonin*, greitoji pagalba, skubios pagalbos
c ATM: bankomat*
c BANK: bank*, bankai
c POST_OFFICE: paštas, pašto skyrius, pašt*
c POLICE: polici*, policijos komisariatas
c CINEMA: kino teatr*, kinas, kino
c TRAIN_STATION: geležinkelio stotis, traukinių stotis, stotis
a VEGAN: veganišk*, vegan*
a VEGETARIAN: vegetariš*, vegetar*
a GLUTEN_FREE: be glitimo, bebriglitimo
a OPEN_24_7: visą parą, 24 valandas, 24 7
u PIZZA: pica*, picerij*, pizza
near_me: šalia manęs, arti manęs, netoliese, artimiausi*, mano apylinkėse
open_now: atidaryta dabar, dabar atidaryta, atidaryta, atidarytas, dirba dabar, veikia dabar, veikia
near_both: netoli, šalia, arti
near: prie, priešais
near_dest: netoli tikslo, šalia tikslo, prie tikslo, arti tikslo, tiksle
route: pagal maršrutą, maršrute, pakeliui, kelyje, pagal kelią
filler: ir, su, už, kuris, kuri, kur, rasti, ieškau, noriu, reikia, geras, gera, geriausias, pigus, yra, ar
"""
}
