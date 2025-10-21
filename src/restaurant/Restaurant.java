package restaurant;

import java.util.*;

/**
 * Proyecto: Gestión de pedidos de restaurante (sin VIP)
 * Modo interactivo (CLI) + simulación aleatoria
 *
 * Estructuras usadas (>=3):
 *   - Arreglo (array)         : MenuItem[] (menú) e int[] (ítems del pedido)
 *   - Arreglo dinámico        : ArrayList<String> como bitácora (log de eventos)
 *   - Cola con prioridad      : PriorityQueue<Pedido> (por canal y antigüedad)
 *   - Cola de preparación     : ArrayDeque<Pedido> colaPreparacion (FIFO en cocina)
 *   - Cola circular despacho  : ArrayDeque<Pedido> colaDespacho (buffer de salida con capacidad fija)
 *
 * Uso:
 *   # Interactivo (menú):
 *   javac Restaurant.java && java Restaurant
 *
 *   # Simulación directa (sin menú):
 *   #   java Restaurant sim [nPedidos<=20] [maxChefs<=5] [seed]
 *   java Restaurant sim 10 3
 */
public class Restaurant {

    // ===== Modelos =====
    enum Canal { DINE_IN, PARA_LLEVAR, DOMICILIO }

    static class MenuItem {
        int codigo; String nombre; double precio; int prepMin;
        MenuItem(int codigo, String nombre, double precio, int prepMin) {
            this.codigo=codigo; this.nombre=nombre; this.precio=precio; this.prepMin=prepMin;
        }
    }

    static class Pedido {
        int id; String cliente; Canal canal; long creadoMs; int[] items;
        Pedido(int id, String cliente, Canal canal, int[] items) {
            this.id=id; this.cliente=cliente; this.canal=canal; this.items=items;
            this.creadoMs = System.currentTimeMillis();
        }
    }

    static class ComparadorPrioridad implements Comparator<Pedido> {
        private int peso(Canal c) { return (c==Canal.DOMICILIO?3 : c==Canal.PARA_LLEVAR?2 : 1); }
        @Override public int compare(Pedido a, Pedido b) {
            int pa=peso(a.canal), pb=peso(b.canal);
            if (pa!=pb) return Integer.compare(pb, pa);
            if (a.creadoMs!=b.creadoMs) return Long.compare(a.creadoMs, b.creadoMs);
            return Integer.compare(a.id, b.id);
        }
    }

    // ===== Gestor =====
    static class Gestor {
        final MenuItem[] menu;
        final PriorityQueue<Pedido> colaPrioridad = new PriorityQueue<>(new ComparadorPrioridad());
        final ArrayDeque<Pedido> colaCocina = new ArrayDeque<>();
        final ArrayDeque<Pedido> colaDespacho = new ArrayDeque<>();
        final EnumMap<Canal,Integer> finalizados = new EnumMap<>(Canal.class);
        long ventasTotales = 0;
        final int capacidadBuffer;

        Gestor(MenuItem[] menu, int capacidadBuffer) {
            this.menu = menu; this.capacidadBuffer = Math.max(5, capacidadBuffer);
            for (Canal c: Canal.values()) finalizados.put(c, 0);
        }

        void log(String s) { System.out.println(s); }

        void encolarParaCocina(Pedido p) {
            colaPrioridad.add(p); log("ENTRA " + p.id + " {" + p.cliente + "} canal=" + p.canal);
        }

        int iniciarHasta(int max, int ronda) {
            int k=0;
            while(k<max && !colaPrioridad.isEmpty()){
                Pedido p = colaPrioridad.poll();
                colaCocina.addLast(p);
                log("INICIA " + p.id + " → cocina (ronda " + ronda + ")");
                k++;
            }
            return k;
        }

        int terminarAlgunos(Random rng, int maxChefs, int ronda) {
            int hechos=0, n=Math.min(maxChefs, colaCocina.size());
            for(int i=0;i<n;i++){
                Pedido p = colaCocina.pollFirst();
                if (p==null) break;
                if (colaDespacho.size()>=capacidadBuffer) colaDespacho.pollFirst();
                colaDespacho.addLast(p);
                ventasTotales += totalPedido(p);
                finalizados.put(p.canal, finalizados.get(p.canal)+1);
                log("TERMINA " + p.id + " → despacho (ronda " + ronda + ")");
                hechos++;
            }
            return hechos;
        }

        int despacharTodoBuffer() {
            int k=0;
            while(!colaDespacho.isEmpty()){
                Pedido p=colaDespacho.pollFirst();
                log("DESPACHADO " + p.id + " {" + p.cliente + "}");
                k++;
            }
            return k;
        }

        long totalPedido(Pedido p) {
            long sum=0;
            for(int cod:p.items){
                MenuItem mi = buscarItemPorCodigo(cod);
                if (mi!=null) sum += Math.round(mi.precio);
            }
            return sum;
        }

        MenuItem buscarItemPorCodigo(int cod) {
            for (MenuItem mi: menu) if (mi!=null && mi.codigo==cod) return mi;
            return null;
        }

        void reporte() {
            UI.clear();
            UI.banner("REPORTE");
            System.out.println();
            System.out.println("Ventas totales: " + UI.moneyCOP(ventasTotales));
            for (Canal c: Canal.values())
                System.out.println(String.format("%-12s: %d", c, finalizados.get(c)));
            System.out.println("Pendientes prioridad: " + colaPrioridad.size());
            System.out.println("En cocina: " + colaCocina.size());
            System.out.println("En despacho: " + colaDespacho.size());
        }
    }

    // ===== Main =====
    public static void main(String[] args) {
        UI.setCols(90);

        MenuItem[] menu = new MenuItem[] {
            new MenuItem(10,"Hamburguesa",     17000,10),
            new MenuItem(11,"Hamburguesa Dbl", 23000,12),
            new MenuItem(20,"Perro Caliente",  12000, 8),
            new MenuItem(30,"Salchipapa",      14000,10),
            new MenuItem(40,"Arepa Rellena",   8000,  7),
            new MenuItem(60,"Gaseosa 400ml",   4000,  0),
            new MenuItem(80,"Jugo Natural",    6000,  0),
        };

        Gestor gestor = new Gestor(menu, 8);
        Scanner sc = new Scanner(System.in);
        Random rng = new Random();

        final String[] OPTS = {
            "Ver menú",
            "Ingresar pedido (manual)",
            "Iniciar hasta N (prioridad → cocina FIFO)",
            "Terminar algunos (cocina FIFO → despacho)",
            "Despachar todo el buffer",
            "Ver reporte",
            "Simular ahora (aleatorio, hasta 20)",
            "Salir"
        };

        int nextId = 1, ronda = 1;
        boolean run = true;

        while (run) {
            int op = -1;
            while (true) {
                UI.clear();
                UI.printMainMenuPlain(OPTS);
                String line = sc.nextLine().trim();
                if (line.matches("[1-8]")) { op = Integer.parseInt(line); break; }
                System.out.println("Opción inválida. Escribe un número del 1 al 8.");
                pauseEnter(sc);
            }

            switch (op) {
                case 1: // Ver menú
                    renderMenu(menu);
                    pauseEnter(sc);
                    break;

                case 2: { // Ingresar pedido
                    UI.clear();
                    UI.banner("Ingresar pedido");
                    System.out.print("Nombre del cliente: ");
                    String nombre = sc.nextLine().trim();
                    int canalNum = leerEnteroRango(sc, 1, 3, "Canal (1=COMEDOR, 2=PARA_LLEVAR, 3=DOMICILIO): ");
                    Canal canal = canalNum==1? Canal.DINE_IN : canalNum==2? Canal.PARA_LLEVAR : Canal.DOMICILIO;

                    System.out.print("Códigos de ítems (p. ej., 10,50,60): ");
                    String[] partes = sc.nextLine().trim().split(",");
                    ArrayList<Integer> tmp = new ArrayList<>();
                    for (String s: partes) {
                        s = s.trim(); if (s.isEmpty()) continue;
                        try { tmp.add(Integer.parseInt(s)); } catch (Exception ignored) {}
                    }
                    if (tmp.isEmpty()) { System.out.println("Sin ítems válidos."); pauseEnter(sc); break; }
                    int[] arr = new int[tmp.size()];
                    for (int i=0;i<tmp.size();i++) arr[i] = tmp.get(i);

                    int id = nextId++;
                    gestor.encolarParaCocina(new Pedido(id, nombre, canal, arr));
                    System.out.println("Pedido #" + id + " {" + nombre + "} canal=" + canal + " registrado");
                    pauseEnter(sc);
                    break;
                }

                case 3: { // Iniciar hasta N
                    UI.clear();
                    UI.banner("Iniciar preparación");
                    int n = leerEnteroRango(sc, 1, 5, "¿Cuántos chefs (1..5)? ");
                    int k = gestor.iniciarHasta(n, ronda++);
                    System.out.println("Iniciados " + k + " pedidos.");
                    pauseEnter(sc);
                    break;
                }

                case 4: { // Terminar algunos
                    UI.clear();
                    UI.banner("Terminar pedidos");
                    int n = leerEnteroRango(sc, 1, 5, "¿Cuántos chefs (1..5)? ");
                    int k = gestor.terminarAlgunos(rng, n, ronda++);
                    System.out.println("Terminados " + k + " pedidos.");
                    pauseEnter(sc);
                    break;
                }

                case 5: { // Despachar
                    UI.clear();
                    UI.banner("Despachar");
                    int k = gestor.despacharTodoBuffer();
                    System.out.println("Despachados " + k + " pedidos.");
                    pauseEnter(sc);
                    break;
                }

                case 6: // Reporte
                    gestor.reporte();
                    pauseEnter(sc);
                    break;

                case 7: { // Simulación
                    UI.clear();
                    UI.banner("Simulación (hasta 20)");
                    int n = leerEnteroRango(sc, 1, 20, "¿Cuántos pedidos (1..20)? ");
                    for (int i=0;i<n;i++){
                        String nombre = "Cli"+(i+1);
                        int pick = rng.nextInt(100);
                        Canal canal = pick<50? Canal.DOMICILIO : pick<80? Canal.PARA_LLEVAR : Canal.DINE_IN;
                        int kItems = 1 + rng.nextInt(3);
                        int[] arr = new int[kItems];
                        for (int j=0;j<kItems;j++){
                            MenuItem mi = menu[rng.nextInt(menu.length)];
                            arr[j] = mi.codigo;
                        }
                        int id = nextId++;
                        gestor.encolarParaCocina(new Pedido(id, nombre, canal, arr));
                    }
                    int started = gestor.iniciarHasta(Math.min(5,n), ronda++);
                    int finished = gestor.terminarAlgunos(rng, Math.min(5,n), ronda++);
                    gestor.despacharTodoBuffer();
                    System.out.println("Simulación: iniciados=" + started + ", terminados=" + finished);
                    pauseEnter(sc);
                    break;
                }

                case 8: // Salir
                    UI.clear();
                    UI.banner("Hasta pronto");
                    run = false;
                    break;
            }
        }
        sc.close();
    }

    // ===== Presentación =====
    private static void renderMenu(MenuItem[] menu) {
        UI.clear();
        UI.banner("MENÚ");
        List<String[]> rows = UI.rows();
        for (MenuItem m: menu) {
            if (m==null) continue;
            rows.add(new String[]{
                String.format("%02d", m.codigo),
                m.nombre,
                UI.moneyCOP(Math.round(m.precio)),
                "~" + m.prepMin + " min"
            });
        }
        UI.table(new String[]{"ID","Producto","Precio","Prep."}, rows);
    }

    private static void pauseEnter(Scanner sc) {
        System.out.print("\nPresiona ENTER para continuar...");
        sc.nextLine();
    }

    // ===== Entrada =====
    private static int leerEnteroRango(Scanner sc, int lo, int hi, String prompt) {
        while (true) {
            System.out.print(prompt);
            String s = sc.nextLine().trim();
            try {
                int x = Integer.parseInt(s);
                if (x < lo || x > hi) throw new NumberFormatException();
                return x;
            } catch (NumberFormatException e) {
                System.out.println("Valor inválido. Usa un número entre " + lo + " y " + hi + ".");
            }
        }
    }
}

