package app.roadstr.core.discovery.lexicon

/** Search vocabulary: English. Also the fallback behind every other language. */
internal object LexiconEn {
    const val TEXT = """
c RESTAURANT: restaurant, restaurants, eatery, eateries, diner, diners, bistro, brasserie
c FAST_FOOD: fast food, fastfood, takeaway restaurant
c CAFE: cafe, cafes, coffee, coffee shop, coffee shops, coffeehouse, tearoom
c BAR: bar, bars, cocktail bar, wine bar
c PUB: pub, pubs, tavern, taverns
c ICE_CREAM: ice cream, ice cream shop, gelato, gelateria, gelato shop
c BAKERY: bakery, bakeries, bakers, patisserie, pastry shop
c SUPERMARKET: supermarket, supermarkets, grocery, groceries, grocery store, grocery shop, hypermarket
c CONVENIENCE: convenience store, corner shop, minimarket, mini market, corner store
c GREENGROCER: greengrocer, greengrocers, vegetable shop, fruit shop, produce store
c BUTCHER: butcher, butchers, butcher shop
c PHARMACY: pharmacy, pharmacies, chemist, chemists, drugstore, drug store
c CLOTHES: clothes shop, clothing store, clothes store, clothing shop, boutique
c ELECTRONICS: electronics store, electronics shop, computer shop, phone shop, electronics
c HARDWARE: hardware store, diy store, home improvement store, hardware shop
c BOOKS: bookshop, bookstore, book shop, book store
c FLORIST: florist, florists, flower shop
c BICYCLE_SHOP: bike shop, bicycle shop, bicycle store, bike store
c MALL: mall, shopping mall, shopping centre, shopping center, outlet
c HAIRDRESSER: hairdresser, hairdressers, hair salon, barber, barbers, barber shop, barbershop
c LAUNDRY: laundry, launderette, laundromat, dry cleaner, dry cleaners
c CAR_REPAIR: car repair, mechanic, auto repair, car mechanic, garage repair
c CAR_WASH: car wash
c BANK: bank, banks
c ATM: atm, atms, cash machine, cash machines, cash point, cashpoint
c POST_OFFICE: post office, post offices
c FUEL: petrol station, petrol stations, gas station, gas stations, fuel station, filling station, petrol, gas, fuel
c CHARGING_STATION: charging station, charging stations, ev charging, ev charger, ev chargers, charger, charge point, electric charging
c PARKING: parking, car park, car parks, parking lot, parking garage, parking space
c TRAIN_STATION: train station, railway station, rail station, station
c BUS_STATION: bus station, bus terminal, coach station
c AIRPORT: airport, airports
c TAXI: taxi, taxis, taxi rank, taxi stand, cab
c HOSPITAL: hospital, hospitals, emergency room
c CLINIC: clinic, clinics, doctor, doctors, medical centre, medical center, gp
c DENTIST: dentist, dentists, dental clinic
c VETERINARY: vet, vets, veterinarian, animal hospital
c HOTEL: hotel, hotels, hostel, hostels, motel, guest house, bed and breakfast, b b, b and b
c CAMPING: camping, campsite, camp site, caravan park
c CINEMA: cinema, cinemas, movie theater, movie theatre, movie theaters
c THEATRE: theatre, theatres, theater, theaters
c MUSEUM: museum, museums
c PARK: park, parks, garden, gardens
c SWIMMING_POOL: swimming pool, swimming pools, pool
c GYM: gym, gyms, fitness centre, fitness center
c LIBRARY: library, libraries
c PLACE_OF_WORSHIP: church, churches, mosque, synagogue, temple, place of worship, cathedral
c TOILETS: toilet, toilets, restroom, restrooms, wc, public toilet, bathroom
c DRINKING_WATER: drinking water, water fountain, water point
c POLICE: police, police station
c FIRE_STATION: fire station, fire brigade
c SCHOOL: school, schools
c UNIVERSITY: university, universities, college
g EAT: eat, food, place to eat, somewhere to eat, dining, lunch, dinner, breakfast, meal
g DRINK: drink, drinks, somewhere to drink
g LODGING: place to stay, somewhere to sleep, accommodation, lodging
a VEGAN: vegan, vegan friendly, plant based
a VEGETARIAN: vegetarian, veggie
a GLUTEN_FREE: gluten free, glutenfree, coeliac, celiac
a HALAL: halal
a KOSHER: kosher
a OUTDOOR_SEATING: outdoor seating, terrace, patio, outdoor tables, beer garden
a WHEELCHAIR: wheelchair accessible, step free, accessible
a WIFI: wifi, wi fi, free wifi
a TAKEAWAY: takeaway, take away, takeout, take out
a DELIVERY: delivery, delivers
a DRIVE_THROUGH: drive through, drive thru
a LPG: lpg, autogas
a CNG: cng, natural gas
a DIESEL: diesel
a OPEN_24_7: 24 7, 24 hours, open 24 hours, all night, round the clock
u PIZZA: pizza, pizzas, pizzeria, pizzerias
u ITALIAN: italian
u SICILIAN: sicilian
u STEAK_HOUSE: steak, steaks, steakhouse, steak house
u BURGER: burger, burgers, hamburger, hamburgers
u SEAFOOD: seafood, fish
u SUSHI: sushi, japanese
u CHINESE: chinese
u INDIAN: indian, curry
u KEBAB: kebab, kebabs, doner
u MEXICAN: mexican, tacos
u THAI: thai
u VIETNAMESE: vietnamese
u GREEK: greek
u SPANISH: spanish, tapas
u FRENCH: french
u GERMAN: german
u LEBANESE: lebanese, falafel
near_me: near me, nearby, close to me, around me, near here, around here, close by, in my area, nearest, closest, near my location, near where i am
open_now: open now, open right now, currently open, open at the moment, open today now
in: in, at
near: near, next to, close to, beside, by the, opposite
near_dest: near my destination, near the destination, near destination, close to my destination, at my destination, at the destination
route: along the route, along my route, on the way, on my route, along the way, en route
filler: the, a, an, of, for, with, and, some, any, good, best, cheap, find, show, search, looking, look, want, need, where, is, are, there, please, i, i m, to, me
"""
}
