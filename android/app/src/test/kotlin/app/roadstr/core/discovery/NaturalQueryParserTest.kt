package app.roadstr.core.discovery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NaturalQueryParserTest {
    private val parser = NaturalQueryParser()

    private fun parse(locale: String, text: String) = parser.interpret(text, locale)

    private val eat = PlaceCategory.members(CategoryGroup.EAT)

    // The eight example queries of the brief.

    @Test
    fun `vegan near me`() {
        val query = parse("it", "vegano nei dintorni")
        assertEquals(QueryIntent.FIND_PLACE, query.intent)
        assertEquals(eat, query.categories)
        assertEquals(setOf(PlaceAttribute.VEGAN), query.attributes)
        assertEquals(LocationConstraint.CurrentLocation, query.location)
        assertTrue(query.locationExplicit)
    }

    @Test
    fun `gluten free in a named city`() {
        val query = parse("it", "gluten free a Firenze")
        assertEquals(QueryIntent.FIND_PLACE, query.intent)
        assertEquals(setOf(PlaceAttribute.GLUTEN_FREE), query.attributes)
        assertEquals(LocationConstraint.NamedPlace("Firenze"), query.location)
    }

    @Test
    fun `steaks in a city carry a leftover word for the web`() {
        val query = parse("it", "bistecche di manzo a Trieste")
        assertEquals(QueryIntent.FIND_PLACE, query.intent)
        assertEquals(setOf(Cuisine.STEAK_HOUSE), query.cuisines)
        assertEquals(eat, query.categories)
        assertEquals(LocationConstraint.NamedPlace("Trieste"), query.location)
        assertEquals(listOf("manzo"), query.residualTerms)
        assertTrue(query.webHint)
    }

    @Test
    fun `pharmacy open now near me`() {
        val query = parse("it", "farmacia aperta vicino a me")
        assertEquals(listOf(PlaceCategory.PHARMACY), query.categories)
        assertTrue(query.openNow)
        assertEquals(LocationConstraint.CurrentLocation, query.location)
        assertTrue(query.locationExplicit)
    }

    @Test
    fun `parking near the destination`() {
        val query = parse("it", "parcheggio vicino alla destinazione")
        assertEquals(listOf(PlaceCategory.PARKING), query.categories)
        assertEquals(LocationConstraint.Destination, query.location)
    }

    @Test
    fun `lpg fuel along the route`() {
        val query = parse("it", "benzina GPL lungo il percorso")
        assertEquals(listOf(PlaceCategory.FUEL), query.categories)
        assertEquals(setOf(PlaceAttribute.LPG), query.attributes)
        assertEquals(LocationConstraint.RouteCorridor, query.location)
    }

    @Test
    fun `an address is left to the classic search`() {
        val query = parse("it", "Via Roma 12 Milano")
        assertEquals(QueryIntent.NAME_OR_ADDRESS, query.intent)
        assertTrue(query.categories.isEmpty())
        assertEquals("Via Roma 12 Milano", query.classicQuery)
    }

    @Test
    fun `a business name is left to the classic search and loses only the near me words`() {
        val query = parse("it", "Esselunga vicino a me")
        assertEquals(QueryIntent.NAME_OR_ADDRESS, query.intent)
        assertEquals("Esselunga", query.classicQuery)
    }

    // Place references.

    @Test
    fun `near a reference place keeps its category`() {
        val query = parse("it", "parcheggio vicino alla stazione")
        assertEquals(listOf(PlaceCategory.PARKING), query.categories)
        assertEquals(LocationConstraint.NearReference("stazione", PlaceCategory.TRAIN_STATION), query.location)
    }

    @Test
    fun `a reference place with a name`() {
        val query = parse("en", "pharmacy near Central Park")
        assertEquals(LocationConstraint.NearReference("Central Park", PlaceCategory.PARK), query.location)
    }

    @Test
    fun `a city before the category is still found`() {
        val query = parse("en", "in Rome pizza")
        assertEquals(LocationConstraint.NamedPlace("Rome"), query.location)
        assertEquals(setOf(Cuisine.PIZZA), query.cuisines)
    }

    @Test
    fun `a trailing word may be a place`() {
        val query = parse("en", "cinema Bologna")
        assertEquals(listOf(PlaceCategory.CINEMA), query.categories)
        assertEquals("Bologna", query.residualPlaceGuess)
        assertFalse(query.locationExplicit)
    }

    @Test
    fun `a leading word may be a place`() {
        val query = parse("en", "Bologna cinema")
        assertEquals("Bologna", query.residualPlaceGuess)
    }

    @Test
    fun `a bare category searches around the user`() {
        val query = parse("it", "farmacia")
        assertEquals(QueryIntent.FIND_PLACE, query.intent)
        assertEquals(LocationConstraint.CurrentLocation, query.location)
        assertFalse(query.locationExplicit)
    }

    @Test
    fun `an attribute that implies no category is not a place search`() {
        assertEquals(QueryIntent.NAME_OR_ADDRESS, parse("en", "wifi").intent)
        assertEquals(QueryIntent.NAME_OR_ADDRESS, parse("en", "open now").intent)
        assertEquals(QueryIntent.NAME_OR_ADDRESS, parse("en", "").intent)
        assertEquals(QueryIntent.NAME_OR_ADDRESS, parse("it", "   ").intent)
    }

    @Test
    fun `a fuel type implies fuel stations`() {
        val query = parse("de", "Autogas in der Nähe")
        assertEquals(listOf(PlaceCategory.FUEL), query.categories)
        assertEquals(setOf(PlaceAttribute.LPG), query.attributes)
    }

    @Test
    fun `more combined queries in the app languages`() {
        assertEquals(setOf(PlaceCategory.BAR, PlaceCategory.CAFE, PlaceCategory.PUB), parse("it", "bar con terrazza vicino alla destinazione").categories.toSet())
        assertEquals(setOf(PlaceAttribute.OUTDOOR_SEATING), parse("it", "bar con terrazza vicino alla destinazione").attributes)
        assertEquals(listOf(PlaceCategory.ELECTRONICS), parse("it", "negozio di elettronica aperto ora").categories)
        assertTrue(parse("it", "negozio di elettronica aperto ora").openNow)
        assertEquals(setOf(Cuisine.SICILIAN), parse("it", "ristorante siciliano a Palermo").cuisines)
    }

    // One or two queries per language.

    @Test
    fun `german`() {
        val query = parse("de", "vegane Restaurants in Berlin")
        assertEquals(listOf(PlaceCategory.RESTAURANT), query.categories)
        assertEquals(setOf(PlaceAttribute.VEGAN), query.attributes)
        assertEquals(LocationConstraint.NamedPlace("Berlin"), query.location)
        val near = parse("de", "Tankstelle in der Nähe")
        assertEquals(listOf(PlaceCategory.FUEL), near.categories)
        assertEquals(LocationConstraint.CurrentLocation, near.location)
        assertTrue(parse("de", "Apotheke jetzt geöffnet").openNow)
    }

    @Test
    fun `french`() {
        val query = parse("fr", "restaurant végétarien près de moi")
        assertEquals(listOf(PlaceCategory.RESTAURANT), query.categories)
        assertEquals(setOf(PlaceAttribute.VEGETARIAN), query.attributes)
        assertTrue(query.locationExplicit)
        val place = parse("fr", "pharmacie ouverte maintenant à Lyon")
        assertTrue(place.openNow)
        assertEquals(LocationConstraint.NamedPlace("Lyon"), place.location)
        assertEquals(LocationConstraint.RouteCorridor, parse("fr", "borne de recharge le long de l'itinéraire").location)
    }

    @Test
    fun `spanish and portuguese`() {
        assertEquals(listOf(PlaceCategory.FUEL), parse("es", "gasolinera cerca de mí").categories)
        val es = parse("es", "farmacia abierta ahora en Madrid")
        assertTrue(es.openNow)
        assertEquals(LocationConstraint.NamedPlace("Madrid"), es.location)
        val pt = parse("pt", "restaurante vegano perto de mim")
        assertEquals(setOf(PlaceAttribute.VEGAN), pt.attributes)
        assertTrue(pt.locationExplicit)
        assertEquals(LocationConstraint.NamedPlace("Lisboa"), parse("pt", "farmácia em Lisboa").location)
    }

    @Test
    fun `dutch polish and romanian`() {
        val nl = parse("nl", "vegan restaurant in de buurt")
        assertEquals(setOf(PlaceAttribute.VEGAN), nl.attributes)
        assertEquals(LocationConstraint.CurrentLocation, nl.location)
        assertTrue(nl.locationExplicit)
        val pl = parse("pl", "apteka otwarta teraz w Krakowie")
        assertEquals(listOf(PlaceCategory.PHARMACY), pl.categories)
        assertTrue(pl.openNow)
        assertEquals(LocationConstraint.NamedPlace("Krakowie"), pl.location)
        val ro = parse("ro", "farmacie deschisă acum lângă mine")
        assertTrue(ro.openNow)
        assertTrue(ro.locationExplicit)
    }

    @Test
    fun `russian greek and bulgarian use their own scripts`() {
        val ru = parse("ru", "ресторан в Москве")
        assertEquals(listOf(PlaceCategory.RESTAURANT), ru.categories)
        assertEquals(LocationConstraint.NamedPlace("Москве"), ru.location)
        assertEquals(listOf(PlaceCategory.PHARMACY), parse("ru", "аптека рядом со мной").categories)
        val el = parse("el", "φαρμακείο κοντά μου")
        assertEquals(listOf(PlaceCategory.PHARMACY), el.categories)
        assertTrue(el.locationExplicit)
        assertEquals(listOf(PlaceCategory.PHARMACY), parse("bg", "аптека близо до мен").categories)
    }

    @Test
    fun `nordic and baltic and slavic languages`() {
        assertEquals(listOf(PlaceCategory.PHARMACY), parse("sv", "apotek nära mig").categories)
        assertEquals(listOf(PlaceCategory.PHARMACY), parse("da", "apotek i nærheden").categories)
        assertEquals(LocationConstraint.NamedPlace("Praze"), parse("cs", "restaurace v Praze").location)
        assertEquals(listOf(PlaceCategory.PHARMACY), parse("sk", "lekáreň otvorené teraz").categories)
        assertEquals(listOf(PlaceCategory.PHARMACY), parse("sl", "lekarna v bližini").categories)
        assertEquals(LocationConstraint.NamedPlace("Zagrebu"), parse("hr", "restoran u Zagrebu").location)
    }

    @Test
    fun `suffix locative languages find the place through the trailing word`() {
        val hu = parse("hu", "étterem Budapesten")
        assertEquals(listOf(PlaceCategory.RESTAURANT), hu.categories)
        assertEquals("Budapesten", hu.residualPlaceGuess)
        val fi = parse("fi", "ravintola Helsingissä")
        assertEquals("Helsingissä", fi.residualPlaceGuess)
        val near = parse("fi", "apteekki lähellä")
        assertEquals(listOf(PlaceCategory.PHARMACY), near.categories)
        assertEquals(LocationConstraint.CurrentLocation, near.location)
        assertTrue(near.locationExplicit)
        val station = parse("fi", "parkki aseman lähellä")
        assertEquals(LocationConstraint.NearReference("aseman", null), station.location)
        assertEquals(listOf(PlaceCategory.PHARMACY), parse("et", "apteek minu lähedal").categories)
        assertEquals(listOf(PlaceCategory.PHARMACY), parse("lt", "vaistinė netoli").categories)
        assertEquals(listOf(PlaceCategory.PHARMACY), parse("lv", "aptieka tuvumā").categories)
    }

    @Test
    fun `maltese and irish`() {
        assertEquals(listOf(PlaceCategory.PHARMACY), parse("mt", "spiżerija qrib tiegħi").categories)
        assertEquals(listOf(PlaceCategory.PHARMACY), parse("ga", "cógaslann in aice liom").categories)
    }

    @Test
    fun `japanese puts the place before the thing`() {
        val city = parse("ja", "東京のカフェ")
        assertEquals(listOf(PlaceCategory.CAFE), city.categories)
        assertEquals(LocationConstraint.NamedPlace("東京"), city.location)
        val near = parse("ja", "近くのカフェ")
        assertEquals(listOf(PlaceCategory.CAFE), near.categories)
        assertEquals(LocationConstraint.CurrentLocation, near.location)
        assertTrue(near.locationExplicit)
        val station = parse("ja", "駅の近くの駐車場")
        assertEquals(listOf(PlaceCategory.PARKING), station.categories)
        assertEquals(LocationConstraint.NearReference("駅", PlaceCategory.TRAIN_STATION), station.location)
    }

    @Test
    fun `chinese puts the place before the thing`() {
        val city = parse("zh", "上海的餐厅")
        assertEquals(listOf(PlaceCategory.RESTAURANT), city.categories)
        assertEquals("上海", city.residualPlaceGuess)
        val near = parse("zh", "附近的药店")
        assertEquals(listOf(PlaceCategory.PHARMACY), near.categories)
        assertEquals(LocationConstraint.CurrentLocation, near.location)
        assertTrue(near.locationExplicit)
        val reference = parse("zh", "火车站附近的酒店")
        assertEquals(LocationConstraint.NearReference("火车站", PlaceCategory.TRAIN_STATION), reference.location)
    }

    // English and edge cases.

    @Test
    fun `english examples`() {
        val vegan = parse("en", "vegan restaurant near me")
        assertEquals(listOf(PlaceCategory.RESTAURANT), vegan.categories)
        assertEquals(setOf(PlaceAttribute.VEGAN), vegan.attributes)
        assertEquals(LocationConstraint.NamedPlace("Florence"), parse("en", "gluten free in Florence").location)
        assertEquals(LocationConstraint.RouteCorridor, parse("en", "petrol station along the route").location)
        assertEquals(LocationConstraint.Destination, parse("en", "parking near my destination").location)
        val steak = parse("en", "steak restaurants in Trieste")
        assertEquals(setOf(Cuisine.STEAK_HOUSE), steak.cuisines)
        assertEquals(LocationConstraint.NamedPlace("Trieste"), steak.location)
        assertEquals(QueryIntent.NAME_OR_ADDRESS, parse("en", "10 Downing Street London").intent)
        assertEquals("starbucks", parse("en", "starbucks near me").classicQuery)
    }

    @Test
    fun `a polite sentence still parses`() {
        val query = parse("en", "please find a good vegan restaurant near me")
        assertEquals(listOf(PlaceCategory.RESTAURANT), query.categories)
        assertEquals(setOf(PlaceAttribute.VEGAN), query.attributes)
        assertTrue(query.residualTerms.isEmpty())
    }

    @Test
    fun `very long input is bounded`() {
        val query = parse("en", "pizza ".repeat(500))
        assertTrue(query.rawText.length <= NaturalQueryParser.MAX_QUERY_CHARS)
    }

    @Test
    fun `a country-less language tag falls back to english words`() {
        assertEquals(listOf(PlaceCategory.PHARMACY), parse("tlh", "pharmacy").categories)
    }

    @Test
    fun `the place text keeps the typed case and spelling`() {
        val query = parse("it", "pizzeria a San Giovanni in Fiore")
        assertEquals(
            LocationConstraint.NamedPlace("San Giovanni in Fiore", listOf("Fiore")),
            query.location,
        )
    }

    @Test
    fun `a descriptive in before the city is offered as the longer reading first`() {
        val query = parse("it", "pizza in teglia a Roma")
        assertEquals(LocationConstraint.NamedPlace("teglia a Roma", listOf("Roma")), query.location)
    }

    @Test
    fun `an unrelated word is not mistaken for a place clause`() {
        assertNull(parse("en", "pizza in").residualPlaceGuess)
        assertEquals(QueryIntent.FIND_PLACE, parse("en", "pizza in").intent)
    }

    @Test
    fun `the query never prints the typed text`() {
        assertEquals("NaturalPlaceQuery(intent=FIND_PLACE)", parse("en", "pharmacy secret street").toString())
    }
}
