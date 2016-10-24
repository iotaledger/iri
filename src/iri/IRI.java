package iri;

class IRI {

    static final String NAME = "IRI";
    static final String VERSION = "1.1.0";

    public static void main(final String[] args) {

        System.out.println(NAME + " " + VERSION);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {

            try {

                API.shutDown();
                TipsManager.shutDown();
                Node.shutDown();
                Storage.shutDown();

            } catch (final Exception e) {
            }

        }, "Shutdown Hook"));

        try {

            Storage.launch();
            Node.launch(args);
            TipsManager.launch();
            API.launch();

        } catch (final Exception e) {

            e.printStackTrace();
        }
    }
}
