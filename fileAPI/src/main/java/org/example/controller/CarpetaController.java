package org.example.controller;

import org.example.model.Archivo;
import org.example.model.Directorio;
import org.example.model.Usuario;
import org.example.util.JsonUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.*;

@RestController
@RequestMapping("/carpetas")
public class CarpetaController {

    // Crear nueva carpeta en una ruta específica
    @PostMapping("/{usuario}/crear")
    public ResponseEntity<?> crearCarpeta(
            @PathVariable String usuario,
            @RequestBody Map<String, String> request) {

        String ruta = request.get("path");         // Ejemplo: "root/documentos"
        String nombreNueva = request.get("nombre"); // Ejemplo: "fotos"

        try {
            Usuario user = JsonUtil.buscarPorNombre(usuario);
            Directorio destino = buscarDirectorioPorRuta(user.getDirectorioRaiz(), ruta);

            for (Directorio sub : destino.getSubdirectorios()) {
                if (sub.getNombre().equals(nombreNueva)) {
                    return ResponseEntity.badRequest().body("Ya existe una carpeta con ese nombre en esta ruta.");
                }
            }

            Directorio nueva = new Directorio(nombreNueva, destino);
            destino.getSubdirectorios().add(nueva);
            destino.setFechaModificacion(java.time.LocalDateTime.now());

            JsonUtil.guardarUsuario(user);
            return ResponseEntity.ok("Carpeta creada correctamente.");
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body("Error al cargar/guardar usuario.");
        } catch (NoSuchElementException e) {
            return ResponseEntity.badRequest().body("Ruta no encontrada.");
        }
    }
    // Listar contenido de una carpeta
    @PostMapping("/{usuario}/listar")
    public ResponseEntity<?> listarContenido(
            @PathVariable String usuario,
            @RequestBody Map<String, String> request) {

        String ruta = request.get("ruta");

        try {
            Usuario user = JsonUtil.buscarPorNombre(usuario);
            Directorio dir = buscarDirectorioPorRuta(user.getDirectorioRaiz(), ruta);

            Map<String, Object> contenido = new HashMap<>();
            contenido.put("archivos", dir.getArchivos());
            contenido.put("subdirectorios", dir.getSubdirectorios());
            return ResponseEntity.ok(contenido);

        } catch (IOException e) {
            return ResponseEntity.internalServerError().body("Error al acceder a datos.");
        } catch (NoSuchElementException e) {
            return ResponseEntity.badRequest().body("Ruta no válida.");
        }
    }

    // Función auxiliar para navegar un path tipo "root/docs/proyectos"
    private Directorio buscarDirectorioPorRuta(Directorio actual, String ruta) {
        String[] partes = ruta.split("/");
        for (String nombre : partes) {
            if (nombre.equals(actual.getNombre())) continue;
            Optional<Directorio> siguiente = actual.getSubdirectorios().stream()
                    .filter(d -> d.getNombre().equals(nombre))
                    .findFirst();
            if (siguiente.isEmpty()) throw new NoSuchElementException("No se encontró: " + nombre);
            actual = siguiente.get();
        }
        return actual;
    }
    @DeleteMapping("/{nombre}")
    public ResponseEntity<?> eliminarCarpeta(
            @PathVariable String nombre,
            @RequestBody Map<String, String> body) {
        try {
            String path = body.get("path");
            if (path == null || path.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Path requerido"));
            }

            Usuario usuario = JsonUtil.buscarPorNombre(nombre);
            Directorio raiz = usuario.getDirectorioRaiz();

            // Separar el último segmento
            String[] partes = path.split("/");
            if (partes.length < 2) {
                return ResponseEntity.badRequest().body(Map.of("error", "No se puede eliminar el directorio raíz"));
            }

            String nombreCarpeta = partes[partes.length - 1];
            String pathPadre = String.join("/", Arrays.copyOf(partes, partes.length - 1));

            Directorio padre = buscarDirectorio(usuario, pathPadre);
            if (padre == null) {
                return ResponseEntity.status(404).body(Map.of("error", "Directorio padre no encontrado"));
            }

            boolean eliminado = padre.eliminarSubdirectorio(nombreCarpeta);
            if (!eliminado) {
                return ResponseEntity.status(404).body(Map.of("error", "Subdirectorio no encontrado"));
            }

            usuario.recalcularEspacioUsado();
            JsonUtil.guardarUsuario(usuario);
            return ResponseEntity.ok(Map.of("mensaje", "Carpeta eliminada correctamente"));

        } catch (IOException e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Error al eliminar la carpeta"));
        }
    }
    private Directorio buscarDirectorio(Usuario usuario, String path) {
        if (path.equals("root")) return usuario.getDirectorioRaiz();
        if (path.equals("compartidos")) return usuario.getDirectorioCompartidos();

        String[] partes = path.split("/");
        Directorio actual = usuario.getDirectorioRaiz();

        for (int i = 1; i < partes.length; i++) {
            String nombre = partes[i];
            Optional<Directorio> sub = actual.getSubdirectorios().stream()
                    .filter(d -> d.getNombre().equals(nombre))
                    .findFirst();
            if (sub.isEmpty()) return null;
            actual = sub.get();
        }

        return actual;
    }
    private boolean buscarRutaDeCarpeta(Directorio actual, String nombreCarpeta, String rutaActual, List<String> resultado) {
        if (actual.getNombre().equals(nombreCarpeta)) {
            resultado.add(rutaActual);
            return true;
        }

        for (Directorio sub : actual.getSubdirectorios()) {
            boolean encontrada = buscarRutaDeCarpeta(sub, nombreCarpeta, rutaActual + "/" + sub.getNombre(), resultado);
            if (encontrada) return true;
        }

        return false;
    }

    @GetMapping("/{nombre}/ruta")
    public ResponseEntity<?> obtenerRutaCarpeta(
            @PathVariable String nombre,
            @RequestParam String nombreCarpeta) {
        try {
            Usuario usuario = JsonUtil.buscarPorNombre(nombre);
            List<String> ruta = new ArrayList<>();
            boolean encontrada = buscarRutaDeCarpeta(usuario.getDirectorioRaiz(), nombreCarpeta, "root", ruta);

            if (!encontrada) {
                return ResponseEntity.status(404).body(Map.of("error", "Carpeta no encontrada"));
            }

            return ResponseEntity.ok(Map.of("ruta", ruta.get(0)));

        } catch (IOException e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Error al buscar el usuario"));
        }
    }

    @PostMapping("/{nombre}/mover")
    public ResponseEntity<?> moverCarpeta(
            @PathVariable("nombre") String nombre,
            @RequestBody Map<String, String> request) {
        try {
            String origenPath = request.get("origenPath");
            String destinoPath = request.get("destinoPath");

            if (origenPath == null || destinoPath == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Faltan rutas en el cuerpo de la solicitud"));
            }

            Usuario usuario = JsonUtil.buscarPorNombre(nombre);

            // separar el nombre de la carpeta a mover
            String[] partesOrigen = origenPath.split("/");
            String nombreCarpeta = partesOrigen[partesOrigen.length - 1];
            String pathPadreOrigen = String.join("/", java.util.Arrays.copyOf(partesOrigen, partesOrigen.length - 1));

            // obtener referencias
            Directorio padreOrigen = buscarDirectorio(usuario, pathPadreOrigen);
            Directorio destino = buscarDirectorio(usuario, destinoPath);

            if (padreOrigen == null || destino == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Directorio origen o destino no encontrado"));
            }

            // buscar el subdirectorio a mover
            Directorio dirAMover = padreOrigen.getSubdirectorios().stream()
                    .filter(d -> d.getNombre().equals(nombreCarpeta))
                    .findFirst()
                    .orElse(null);

            if (dirAMover == null) {
                return ResponseEntity.status(404).body(Map.of("error", "Carpeta de origen no encontrada"));
            }

            // verificar si ya existe en destino
            boolean existe = destino.getSubdirectorios().stream()
                    .anyMatch(d -> d.getNombre().equals(nombreCarpeta));
            if (existe) {
                return ResponseEntity.status(409).body(Map.of("error", "Ya existe una carpeta con ese nombre en el destino"));
            }

            // mover: eliminar del padre original
            padreOrigen.getSubdirectorios().remove(dirAMover);

            // actualizar referencia de padre
            dirAMover.setPadre(destino);

            // agregar al destino
            destino.getSubdirectorios().add(dirAMover);

            // actualizar fechas
            dirAMover.setFechaModificacion(java.time.LocalDateTime.now());
            padreOrigen.setFechaModificacion(java.time.LocalDateTime.now());
            destino.setFechaModificacion(java.time.LocalDateTime.now());

            // recalcular espacio
            usuario.recalcularEspacioUsado();
            JsonUtil.guardarUsuario(usuario);

            return ResponseEntity.ok(Map.of(
                    "mensaje", "Carpeta movida correctamente",
                    "nuevoPath", destinoPath + "/" + nombreCarpeta
            ));

        } catch (IOException e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Error procesando el movimiento: " + e.getMessage()));
        }
    }

    @PostMapping("/{nombre}/compartir")
    public ResponseEntity<?> compartirCarpeta(
            @PathVariable String nombre,   // usuario origen
            @RequestBody Map<String, String> request) {
        try {
            String path = request.get("path");  // ruta completa de la carpeta a compartir
            String destinatario = request.get("destinatario");

            Usuario emisor = JsonUtil.buscarPorNombre(nombre);
            Usuario receptor = JsonUtil.buscarPorNombre(destinatario);

            // localizar la carpeta origen
            Directorio carpetaOriginal = buscarDirectorio(emisor, path);
            if (carpetaOriginal == null) {
                return ResponseEntity.status(404).body(Map.of("error", "Carpeta no encontrada"));
            }

            // verificar si ya existe en el destino (compartidos)
            boolean yaExiste = receptor.getDirectorioCompartidos().getSubdirectorios().stream()
                    .anyMatch(d -> d.getNombre().equals(carpetaOriginal.getNombre()));

            if (yaExiste) {
                return ResponseEntity.status(409).body(Map.of(
                        "error", "Ya existe una carpeta con ese nombre en la carpeta de compartidos del destinatario"
                ));
            }

            // clonar la carpeta completa recursivamente
            Directorio copia = clonarDirectorio(carpetaOriginal, null);

            receptor.getDirectorioCompartidos().getSubdirectorios().add(copia);
            receptor.recalcularEspacioUsado();
            JsonUtil.guardarUsuario(receptor);

            return ResponseEntity.ok(Map.of("mensaje", "Carpeta compartida correctamente"));

        } catch (IOException e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Error al compartir carpeta: " + e.getMessage()));
        }
    }

    private Directorio clonarDirectorio(Directorio original, Directorio padre) {
        Directorio copia = new Directorio(original.getNombre(), padre);

        // clonar archivos
        for (Archivo a : original.getArchivos()) {
            copia.getArchivos().add(new Archivo(a.getNombre(), a.getExtension(), a.getContenido()));
        }

        // clonar subdirectorios
        for (Directorio sub : original.getSubdirectorios()) {
            copia.getSubdirectorios().add(clonarDirectorio(sub, copia));
        }

        return copia;
    }




}
