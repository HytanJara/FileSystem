package org.example.controller;

import org.example.model.Archivo;
import org.example.model.Directorio;
import org.example.model.Usuario;
import org.example.util.JsonUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/usuarios/{nombre}/archivos")
public class ArchivoController {

    // Busca un directorio dentro del árbol usando una ruta tipo "root/Carpeta/Sub"
    private Directorio buscarDirectorio(Usuario usuario, String path) {
        String[] partes = path.split("/");
        Directorio actual = partes[0].equalsIgnoreCase("root")
                ? usuario.getDirectorioRaiz()
                : usuario.getDirectorioCompartidos();

        for (int i = 1; i < partes.length && actual != null; i++) {
            actual = actual.getSubdirectorioPorNombre(partes[i]);
        }
        return actual;
    }

    // Crear archivo en un path dado
    @PostMapping
    public ResponseEntity<?> crearArchivo(
            @PathVariable String nombre,
            @RequestBody Map<String, String> archivoData) {
        try {
            Usuario usuario = JsonUtil.buscarPorNombre(nombre);

            // Obtener valores del cuerpo JSON
            String path = archivoData.get("path");
            String nombreArchivo = archivoData.get("nombre");
            String extension = archivoData.get("extension");
            String contenido = archivoData.get("contenido");

            // Verificar ruta válida
            Directorio dir = buscarDirectorio(usuario, path);
            if (dir == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Directorio no encontrado"));
            }

            // Verificar duplicado
            boolean yaExiste = dir.getArchivos().stream()
                    .anyMatch(a -> a.getNombre().equals(nombreArchivo) && a.getExtension().equals(extension));
            if (yaExiste) {
                return ResponseEntity.status(409).body(Map.of(
                        "error", "Ya existe un archivo con ese nombre en este directorio"
                ));
            }

            // Verificar espacio
            Archivo nuevo = new Archivo(nombreArchivo, extension, contenido);
            if (!usuario.puedeAgregarArchivo(nuevo.getTamano())) {
                return ResponseEntity.status(413).body(Map.of("error", "No hay suficiente espacio disponible"));
            }

            // Crear archivo
            dir.agregarArchivo(nuevo);
            usuario.recalcularEspacioUsado();
            JsonUtil.guardarUsuario(usuario);
            return ResponseEntity.ok(nuevo);

        } catch (IOException e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Error al guardar el archivo"));
        }
    }



    // Listar archivos de un directorio
    @GetMapping
    public ResponseEntity<?> listarArchivos(
            @PathVariable String nombre,
            @RequestParam String path) {

        try {
            Usuario usuario = JsonUtil.buscarPorNombre(nombre);
            Directorio dir = buscarDirectorio(usuario, path);
            if (dir == null) return ResponseEntity.badRequest().body("Directorio no encontrado");
            return ResponseEntity.ok(dir.getArchivos());

        } catch (IOException e) {
            return ResponseEntity.internalServerError().body("Error al cargar archivos: " + e.getMessage());
        }
    }

    @DeleteMapping
    public ResponseEntity<?> borrarArchivo(
            @PathVariable String nombre,
            @RequestBody Map<String, String> data) {
        try {
            Usuario usuario = JsonUtil.buscarPorNombre(nombre);
            String path = data.get("path");
            String nombreArchivo = data.get("nombre");
            String extension = data.get("extension");

            Directorio dir = buscarDirectorio(usuario, path);
            if (dir == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Directorio no encontrado"));
            }

            boolean eliminado = dir.eliminarArchivo(nombreArchivo, extension);
            if (!eliminado) {
                return ResponseEntity.status(404).body(Map.of("error", "Archivo no encontrado"));
            }

            usuario.recalcularEspacioUsado();
            JsonUtil.guardarUsuario(usuario);
            return ResponseEntity.ok(Map.of("mensaje", "Archivo eliminado correctamente"));

        } catch (IOException e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Error al eliminar archivo"));
        }
    }


    @PatchMapping("/modificar")
    public ResponseEntity<?> modificarArchivo(
            @PathVariable String nombre,
            @RequestBody Map<String, String> request) {
        try {
            String path = request.get("path");
            String nombreArchivo = request.get("nombreArchivo");
            String nuevoContenido = request.get("nuevoContenido");

            if (path == null || nombreArchivo == null || nuevoContenido == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Faltan datos obligatorios: path, nombreArchivo o nuevoContenido"));
            }

            Usuario usuario = JsonUtil.buscarPorNombre(nombre);
            Directorio dir = buscarDirectorio(usuario, path);
            if (dir == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Directorio no encontrado"));
            }

            Archivo archivo = dir.getArchivos().stream()
                    .filter(a -> a.getNombreCompleto().equals(nombreArchivo))
                    .findFirst()
                    .orElse(null);

            if (archivo == null) {
                return ResponseEntity.status(404).body(Map.of("error", "Archivo no encontrado"));
            }

            archivo.setContenido(nuevoContenido);
            usuario.recalcularEspacioUsado();
            JsonUtil.guardarUsuario(usuario);

            return ResponseEntity.ok(Map.of(
                    "mensaje", "Archivo modificado correctamente",
                    "tamanoActual", archivo.getTamano(),
                    "fechaModificacion", archivo.getFechaModificacion()
            ));

        } catch (IOException e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Error al modificar archivo: " + e.getMessage()));
        }
    }

    @PostMapping("/compartir")
    public ResponseEntity<?> compartirArchivo(
            @PathVariable String nombre,
            @RequestBody Map<String, String> request) {

        try {
            String path = request.get("path");
            String nombreArchivo = request.get("nombreArchivo");
            String destinatario = request.get("destinatario");

            if (path == null || nombreArchivo == null || destinatario == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Faltan datos requeridos"));
            }

            Usuario emisor = JsonUtil.buscarPorNombre(nombre);
            Usuario receptor = JsonUtil.buscarPorNombre(destinatario);

            Directorio dirOrigen = buscarDirectorio(emisor, path);
            if (dirOrigen == null) return ResponseEntity.badRequest().body(Map.of("error", "Directorio origen no encontrado"));

            Archivo original = dirOrigen.getArchivos().stream()
                    .filter(a -> a.getNombreCompleto().equals(nombreArchivo))
                    .findFirst()
                    .orElse(null);

            if (original == null) return ResponseEntity.status(404).body(Map.of("error", "Archivo no encontrado"));

            // Clonar el archivo
            Archivo copia = new Archivo(original.getNombre(), original.getExtension(), original.getContenido());

            if (!receptor.puedeAgregarArchivo(copia.getTamano())) {
                return ResponseEntity.status(413).body(Map.of("error", "El destinatario no tiene espacio suficiente"));
            }

            receptor.getDirectorioCompartidos().agregarArchivo(copia);
            receptor.recalcularEspacioUsado();
            JsonUtil.guardarUsuario(receptor);

            return ResponseEntity.ok(Map.of("mensaje", "Archivo compartido exitosamente"));

        } catch (IOException e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Error al compartir archivo: " + e.getMessage()));
        }
    }

    @GetMapping("/descargar")
    public ResponseEntity<?> descargarArchivo(
            @PathVariable String nombre,
            @RequestParam String path,
            @RequestParam String nombreArchivo) {

        try {
            Usuario usuario = JsonUtil.buscarPorNombre(nombre);
            Directorio dir = buscarDirectorio(usuario, path);
            if (dir == null) return ResponseEntity.badRequest().body(Map.of("error", "Directorio no encontrado"));

            Archivo archivo = dir.getArchivos().stream()
                    .filter(a -> a.getNombreCompleto().equals(nombreArchivo))
                    .findFirst()
                    .orElse(null);

            if (archivo == null) {
                return ResponseEntity.status(404).body(Map.of("error", "Archivo no encontrado"));
            }

            return ResponseEntity.ok()
                    .header("Content-Disposition", "attachment; filename=\"" + archivo.getNombreCompleto() + "\"")
                    .body(archivo.getContenido());

        } catch (IOException e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Error al descargar archivo: " + e.getMessage()));
        }
    }

    private String buscarRutaArchivo(Directorio directorio, String nombreArchivo) {
        for (Archivo archivo : directorio.getArchivos()) {
            if (archivo.getNombre().equals(nombreArchivo)) {
                return directorio.getNombre();
            }
        }

        for (Directorio subdir : directorio.getSubdirectorios()) {
            String resultado = buscarRutaArchivo(subdir, nombreArchivo);
            if (resultado != null) {
                return directorio.getNombre() + "/" + resultado;
            }
        }

        return null;
    }
    @GetMapping("/ruta")
    public ResponseEntity<?> obtenerRutaArchivo(
            @PathVariable String nombre,
            @RequestParam String nombreArchivo) {

        try {
            Usuario usuario = JsonUtil.buscarPorNombre(nombre);
            String path = buscarRutaArchivo(usuario.getDirectorioRaiz(), nombreArchivo);
            if (path == null) {
                return ResponseEntity.status(404).body(Map.of("error", "Archivo no encontrado"));
            }
            return ResponseEntity.ok(Map.of("ruta", path));
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Error al buscar el usuario"));
        }
    }

    @PostMapping("/copiar")
    public ResponseEntity<?> copiarArchivo(
            @PathVariable("nombre") String nombre,
            @RequestBody Map<String, String> request) {
        try {
            Usuario user = JsonUtil.buscarPorNombre(nombre);

            String origenPath = request.get("origenPath");   // solo la ruta al directorio origen
            String destinoPath = request.get("destinoPath"); // solo la ruta al directorio destino
            String nombreArchivo = request.get("nombre");
            String extension = request.get("extension");

            Directorio origenDir = buscarDirectorio(user, origenPath);
            Directorio destinoDir = buscarDirectorio(user, destinoPath);

            if (origenDir == null || destinoDir == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Directorio origen o destino no encontrado"));
            }

            // buscar archivo en el origen
            Optional<Archivo> archivoOriginal = origenDir.getArchivos().stream()
                    .filter(a -> a.getNombre().equals(nombreArchivo) && a.getExtension().equals(extension))
                    .findFirst();

            if (archivoOriginal.isEmpty()) {
                return ResponseEntity.status(404).body(Map.of("error", "Archivo de origen no encontrado"));
            }

            // verificar si ya existe en destino
            boolean existe = destinoDir.getArchivos().stream()
                    .anyMatch(a -> a.getNombre().equals(nombreArchivo) && a.getExtension().equals(extension));
            if (existe) {
                return ResponseEntity.status(409).body(Map.of("error", "Ya existe un archivo con ese nombre en el destino"));
            }

            // crear copia
            Archivo copia = new Archivo(nombreArchivo, extension, archivoOriginal.get().getContenido());

            if (!user.puedeAgregarArchivo(copia.getTamano())) {
                return ResponseEntity.status(413).body(Map.of("error", "No hay espacio suficiente para copiar el archivo"));
            }

            destinoDir.getArchivos().add(copia);
            user.recalcularEspacioUsado();
            JsonUtil.guardarUsuario(user);

            return ResponseEntity.ok(Map.of("mensaje", "Archivo copiado correctamente"));

        } catch (IOException e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Error procesando la copia"));
        }
    }
    // mover archivos
    @PostMapping("/mover")
    public ResponseEntity<?> moverArchivo(
            @PathVariable("nombre") String nombre, // corregido
            @RequestBody Map<String, String> request) {
        try {
            Usuario user = JsonUtil.buscarPorNombre(nombre);

            String origenPath = request.get("origenPath");   // solo directorio
            String destinoPath = request.get("destinoPath"); // solo directorio
            String nombreArchivo = request.get("nombre");
            String extension = request.get("extension");

            Directorio origenDir = buscarDirectorio(user, origenPath);
            Directorio destinoDir = buscarDirectorio(user, destinoPath);

            if (origenDir == null || destinoDir == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Directorio origen o destino no encontrado"));
            }

            // buscar archivo en origen
            Optional<Archivo> archivoOriginal = origenDir.getArchivos().stream()
                    .filter(a -> a.getNombre().equals(nombreArchivo) && a.getExtension().equals(extension))
                    .findFirst();

            if (archivoOriginal.isEmpty()) {
                return ResponseEntity.status(404).body(Map.of("error", "Archivo de origen no encontrado"));
            }

            // verificar si ya existe en destino
            boolean existe = destinoDir.getArchivos().stream()
                    .anyMatch(a -> a.getNombre().equals(nombreArchivo) && a.getExtension().equals(extension));
            if (existe) {
                return ResponseEntity.status(409).body(Map.of("error", "Ya existe un archivo con ese nombre en el destino"));
            }

            // mover significa: cambiar de referencia sin clonar
            Archivo archivoMovido = archivoOriginal.get();

            // eliminar del origen
            origenDir.getArchivos().remove(archivoMovido);

            // agregar al destino
            destinoDir.getArchivos().add(archivoMovido);

            // actualizar fechas
            archivoMovido.setFechaModificacion(java.time.LocalDateTime.now());
            origenDir.setFechaModificacion(java.time.LocalDateTime.now());
            destinoDir.setFechaModificacion(java.time.LocalDateTime.now());

            // recalcular espacio (opcional pero consistente)
            user.recalcularEspacioUsado();

            // guardar
            JsonUtil.guardarUsuario(user);

            return ResponseEntity.ok(Map.of(
                    "mensaje", "Archivo movido correctamente",
                    "nuevoPath", destinoPath + "/" + nombreArchivo + "." + extension
            ));

        } catch (IOException e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Error procesando el movimiento"));
        }
    }





}
