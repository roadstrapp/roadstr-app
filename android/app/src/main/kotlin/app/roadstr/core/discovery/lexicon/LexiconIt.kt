package app.roadstr.core.discovery.lexicon

/** Search vocabulary: Italian. */
internal object LexiconIt {
    const val TEXT = """
c RESTAURANT: ristorante, ristoranti, trattoria, trattorie, osteria, osterie, rosticceria, tavola calda
c FAST_FOOD: fast food, fastfood
c CAFE: caffe, caffetteria, caffetterie, caffetter*
c PUB: pub, birreria, birrerie
c ICE_CREAM: gelato, gelati, gelateria, gelaterie
c BAKERY: panificio, panifici, forno, forni, panetteria, pasticceria, pasticcerie
c SUPERMARKET: supermercato, supermercati, ipermercato, ipermercati
c CONVENIENCE: alimentari, minimarket, negozio di alimentari
c GREENGROCER: fruttivendolo, fruttivendoli, ortofrutta, frutta e verdura
c BUTCHER: macelleria, macellerie, macellaio
c PHARMACY: farmacia, farmacie, parafarmacia
c CLOTHES: negozio di abbigliamento, negozi di abbigliamento, abbigliamento, boutique
c ELECTRONICS: negozio di elettronica, negozi di elettronica, elettronica, negozio di informatica
c HARDWARE: ferramenta, brico, bricolage, fai da te
c BOOKS: libreria, librerie
c FLORIST: fioraio, fiorista, fiorai, negozio di fiori
c BICYCLE_SHOP: negozio di bici, negozio di biciclette, riparazione bici
c MALL: centro commerciale, centri commerciali, outlet
c HAIRDRESSER: parrucchiere, parrucchieri, parrucchiera, barbiere, barbieri
c LAUNDRY: lavanderia, lavanderie, tintoria
c CAR_REPAIR: officina, officine, meccanico, meccanici, carrozzeria, gommista
c CAR_WASH: autolavaggio, autolavaggi, lavaggio auto
c BANK: banca, banche
c ATM: bancomat, sportello bancomat, sportello automatico
c POST_OFFICE: ufficio postale, uffici postali, posta, poste
c FUEL: benzina, benzinaio, benzinai, distributore, distributori, distributore di benzina, stazione di servizio, pompa di benzina, carburante, rifornimento
c CHARGING_STATION: colonnina, colonnine, colonnina elettrica, colonnine elettriche, stazione di ricarica, punto di ricarica, ricarica elettrica, ricarica auto elettrica
c PARKING: parcheggio, parcheggi, posteggio, autorimessa
c TRAIN_STATION: stazione, stazione ferroviaria, stazione dei treni, stazione treni
c BUS_STATION: autostazione, stazione autobus, stazione degli autobus, terminal bus
c AIRPORT: aeroporto, aeroporti
c TAXI: taxi, posteggio taxi
c HOSPITAL: ospedale, ospedali, pronto soccorso
c CLINIC: clinica, cliniche, medico, dottore, guardia medica, ambulatorio, studio medico, poliambulatorio
c DENTIST: dentista, dentisti, studio dentistico
c VETERINARY: veterinario, veterinari, clinica veterinaria
c HOTEL: hotel, albergo, alberghi, ostello, ostelli, motel, agriturismo, bed and breakfast
c CAMPING: campeggio, campeggi, area camper, area sosta camper
c CINEMA: cinema, multisala
c THEATRE: teatro, teatri
c MUSEUM: museo, musei
c PARK: parco, parchi, giardino, giardini
c SWIMMING_POOL: piscina, piscine
c GYM: palestra, palestre, centro fitness
c LIBRARY: biblioteca, biblioteche
c PLACE_OF_WORSHIP: chiesa, chiese, moschea, sinagoga, cattedrale, duomo, basilica, tempio
c TOILETS: bagno, bagni, bagni pubblici, toilette, gabinetto
c DRINKING_WATER: acqua potabile, fontanella, fontanelle
c POLICE: polizia, carabinieri, questura, commissariato
c FIRE_STATION: vigili del fuoco, pompieri, caserma dei pompieri
c SCHOOL: scuola, scuole
c UNIVERSITY: universita, ateneo
g EAT: mangiare, cibo, pranzo, cena, colazione, spuntino
g DRINK: bar, bere, aperitivo, aperitivi, da bere
g LODGING: dormire, alloggio, pernottamento
a VEGAN: vegano, vegana, vegani, vegane
a VEGETARIAN: vegetariano, vegetariana, vegetariani, vegetariane
a GLUTEN_FREE: senza glutine, celiaci, celiaco, celiachia, per celiaci
a HALAL: halal
a KOSHER: kosher
a OUTDOOR_SEATING: terrazza, dehors, all aperto, tavoli all aperto, con giardino
a WHEELCHAIR: accessibile ai disabili, accessibile in sedia a rotelle, accessibile, per disabili, sedia a rotelle
a WIFI: con wifi
a TAKEAWAY: asporto, da asporto, d asporto
a DELIVERY: a domicilio, domicilio, consegna a domicilio
a LPG: gpl
a CNG: metano
a DIESEL: gasolio
a OPEN_24_7: 24 ore, h24, 24 ore su 24, sempre aperto, aperto tutta la notte
u PIZZA: pizze, pizzeria, pizzerie, pizza al taglio
u ITALIAN: italiano, italiana, cucina italiana
u SICILIAN: siciliano, siciliana, cucina siciliana
u STEAK_HOUSE: bistecca, bistecche, tagliata, braceria, fiorentina
u BURGER: hamburgheria
u SEAFOOD: pesce, frutti di mare
u SUSHI: giapponese, giapponesi
u CHINESE: cinese, cinesi
u INDIAN: indiano, indiani, cucina indiana
u KEBAB: kebabbaro
u MEXICAN: messicano, messicani
u THAI: thailandese
u VIETNAMESE: vietnamita
u GREEK: greco, greci
u SPANISH: spagnolo
u FRENCH: francese
u GERMAN: tedesco
u LEBANESE: libanese
near_me: nei dintorni, nei paraggi, qui vicino, vicino a me, vicino me, intorno a me, attorno a me, nelle vicinanze, li vicino, qua vicino, a due passi, piu vicino, piu vicina, vicino a dove sono, vicino alla mia posizione, nelle mie vicinanze, il piu vicino, la piu vicina
open_now: aperto ora, aperta ora, aperti ora, aperte ora, aperto adesso, aperta adesso, aperti adesso, aperto in questo momento, aperti in questo momento, ora aperto, adesso aperto, aperto, aperta, aperti, aperte
in: a, in, ad, nel, nella, nello, nell, nei, negli, nelle
near: vicino a, vicino al, vicino allo, vicino alla, vicino all, vicino ai, vicino agli, vicino alle, accanto a, accanto al, accanto alla, nei pressi di, nei pressi del, nei pressi della, presso, di fronte a, di fronte al, di fronte alla
near_dest: vicino alla destinazione, vicino a destinazione, vicino alla mia destinazione, nei pressi della destinazione, presso la destinazione, alla destinazione, a destinazione, vicino all arrivo
route: lungo il percorso, lungo la strada, sul percorso, lungo il tragitto, sul tragitto, lungo la rotta, sulla strada
filler: di, del, della, dello, dei, degli, delle, il, lo, la, le, gli, i, un, una, uno, con, per, e, ed, che, dove, trovare, trova, cerca, cercare, voglio, vorrei, mi, serve, ci, c, l, d, dell, all, dall, al, alla, allo, ai, agli, alle, buono, buona, ottimo, migliore, migliori, economico, da, ce
"""
}
