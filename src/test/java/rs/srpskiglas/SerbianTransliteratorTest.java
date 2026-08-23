package rs.srpskiglas;

public final class SerbianTransliteratorTest {
    private static void expect(String actual, String expected) {
        if (!actual.equals(expected)) {
            throw new AssertionError("Expected: " + expected + "\nActual:   " + actual);
        }
    }

    public static void main(String[] args) {
        expect(SerbianTransliterator.convert(
                "Ljubav i njiva džem i đak injekcija je primljena u ponedeljak " +
                "on je nadživeo svog prijatelja konj je stajao pored žbunja"),
                "Љубав и њива џем и ђак инјекција је примљена у понедељак " +
                "он је надживео свог пријатеља коњ је стајао поред жбуња");

        expect(SerbianTransliterator.convert(
                "Njegoš je rekao zarez danas idemo u Niš tačka da li je Anđela stigla " +
                "upitnik odjednom se pojavio odžak iznad kuće tačka konjukcija i " +
                "injekcija su neobične reči tačka"),
                "Његош је рекао, данас идемо у Ниш. Да ли је Анђела стигла? " +
                "Одједном се појавио оџак изнад куће. Конјункција и " +
                "инјекција су необичне речи.");

        expect(SerbianTransliterator.convert(
                "Danas dolazim zarez ali kasnim tačka da li čekaš upitnik požuri uzvičnik"),
                "Данас долазим, али касним. Да ли чекаш? Пожури!");

        expect(SerbianTransliterator.convert(
                "Ovo nije dobar znak tačka"),
                "Ово није добар знак.");

        expect(SerbianTransliterator.convert(
                "Prvi red novi red drugi red"),
                "Први ред\nДруги ред");

        expect(SerbianTransliterator.convert(
                "Prvi red nov red drugi red"),
                "Први ред\nДруги ред");

        expect(SerbianTransliterator.convertLatin(
                "Prvi red komanda novi red drugi red"),
                "Prvi red komanda\nDrugi red");

        expect(SerbianTransliterator.convert(
                "Rekao je navodnik dolazim sutra navodnik tačka zarez zatim je otišao"),
                "Рекао је „долазим сутра“; затим је отишао");

        expect(SerbianTransliterator.convertLatin(
                "Rekao je navodnik dolazim sutra navodnik tačka zarez zatim je otišao"),
                "Rekao je „dolazim sutra“; zatim je otišao");

        expect(SerbianTransliterator.convert(
                "Danas su džem i Đak na stolu tačka Đak je stigao tačka"),
                "Данас су џем и ђак на столу. Ђак је стигао.");

        expect(SerbianTransliterator.convert(
                "Da li dolaziš upitnik"),
                "Да ли долазиш?");

        expect(SerbianTransliterator.convertLatin(
                "Da li dolaziš upitnik"),
                "Da li dolaziš?");

        expect(SerbianTransliterator.convert(
                "Koristim Google YouTube WhatsApp i ChatGPT"),
                "Користим Google YouTube WhatsApp и ChatGPT");

        expect(SerbianTransliterator.convert(
                "Vozim Hyundai i koristim Microsoft programe"),
                "Возим Hyundai и користим Microsoft програме");

        expect(SerbianTransliterator.convert(
                "Naš narod u Republici Srpskoj još uvek češće piše latinicom zarez "
                        + "jer su tako učeni od malih nogu tačka"),
                "Наш народ у Републици Српској још увек чешће пише латиницом, "
                        + "јер су тако учени од малих ногу.");

        expect(SerbianTransliterator.convert(
                "Objašnjenje dve tačke navodnik prvi kosa crta "
                        + "drugi navodnik"),
                "Објашњење: „први/други“");

        expect(SerbianTransliterator.convertLatin(
                "Objašnjenje dvotačka navodnik prvi kosa crta "
                        + "drugi navodnik"),
                "Objašnjenje: „prvi/drugi“");

        expect(SerbianTransliterator.convert(
                "Пример otvorena zagrada dodatak zatvorena zagrada "
                        + "tačka zarez nastavak"),
                "Пример (додатак); наставак");

        expect(SerbianTransliterator.normalizeUnexpectedCapitals(
                "Ово Зато што је добро", true),
                "Ово зато што је добро");

        expect(SerbianTransliterator.normalizeUnexpectedCapitals(
                "Сада ћемо Значи наставити", true),
                "Сада ћемо значи наставити");

        expect(SerbianTransliterator.normalizeUnexpectedCapitals(
                "Да ли дворана Београдска арена Има климу?", true),
                "Да ли дворана Београдска арена има климу?");

        expect(SerbianTransliterator.normalizeUnexpectedCapitals(
                "Не могу да нађем у Којој реченици. Међутим Ево сад је добро", true),
                "Не могу да нађем у којој реченици. Међутим ево сад је добро");

        expect(SerbianTransliterator.normalizeUnexpectedCapitals(
                "Ne mogu da nađem u Kojoj rečenici. Međutim Evo sad je dobro", true),
                "Ne mogu da nađem u kojoj rečenici. Međutim evo sad je dobro");

        expect(SerbianTransliterator.normalizeUnexpectedCapitals(
                "ОК, Хоћеш ли приступити изради следеће исправке?", true),
                "ОК, хоћеш ли приступити изради следеће исправке?");

        expect(SerbianTransliterator.normalizeUnexpectedCapitals(
                "Хоћеш ли приступити изради следеће исправке?", true),
                "Хоћеш ли приступити изради следеће исправке?");

        expect(SerbianTransliterator.normalizeUnexpectedCapitals(
                "Да ли сад у овом пројекту могу разговоре да започињем у Новом ћаскању?", true),
                "Да ли сад у овом пројекту могу разговоре да започињем у новом ћаскању?");

        expect(SerbianTransliterator.normalizeUnexpectedCapitals(
                "Ово више није Само списак унапред познатих речи", true),
                "Ово више није само списак унапред познатих речи");

        expect(SerbianTransliterator.normalizeUnexpectedCapitals(
                "Идемо у Нови Сад, а сутра у Ниш", true),
                "Идемо у Нови Сад, а сутра у Ниш");

        expect(SerbianTransliterator.normalizeUnexpectedCapitals(
                "Користим Google и mBanking, а возим Hyundai", true),
                "Користим Google и mBanking, а возим Hyundai");

        expect(SerbianTransliterator.normalizeUnexpectedCapitals(
                "Имена су Владимир, Иван, Влада, Љубица и Злата", true),
                "Имена су Владимир, Иван, Влада, Љубица и Злата");

        expect(SerbianTransliterator.normalizeUnexpectedCapitals(
                "Imena su Vladimir, Ivan, Vlada, Ljubica i Zlata", true),
                "Imena su Vladimir, Ivan, Vlada, Ljubica i Zlata");

        expect(SerbianTransliterator.normalizeUnexpectedCapitals(
                "Sada ćemo Znači nastaviti", true),
                "Sada ćemo znači nastaviti");

        expect(SerbianTransliterator.normalizeUnexpectedCapitals(
                "Данас идемо у Ниш. Онда зовемо ОК сервис", true),
                "Данас идемо у Ниш. Онда зовемо ОК сервис");

        expect(SerbianTransliterator.normalizeUnexpectedCapitals(
                "За мене је добро", false),
                "за мене је добро");

        expect(SerbianTransliterator.normalizeUnexpectedCapitals(
                "ОК Хајде да видимо", true),
                "ОК хајде да видимо");

        expect(SerbianTransliterator.normalizeUnexpectedCapitals(
                "OK Hajde da vidimo", true),
                "OK hajde da vidimo");

        expect(SerbianTransliterator.normalizeUnexpectedCapitals(
                "Ок хајде да видимо", true),
                "ОК хајде да видимо");

        expect(SerbianTransliterator.normalizeUnexpectedCapitals(
                "Sve je ok", true),
                "Sve je OK");

        System.out.println("All Serbian transliteration tests passed.");
    }
}
