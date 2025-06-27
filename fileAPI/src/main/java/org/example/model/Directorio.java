package org.example.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

public class Directorio {
    private String id;
    private String nombre;
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaModificacion;
    private List<Archivo> archivos;
    private List<Directorio> subdirectorios;

    @JsonIgnore
    private Directorio padre;

    public Directorio() {
        this.id = UUID.randomUUID().toString();
        this.fechaCreacion = LocalDateTime.now();
        this.fechaModificacion = LocalDateTime.now();
        this.archivos = new ArrayList<>();
        this.subdirectorios = new ArrayList<>();
    }

    public Directorio(String nombre, Directorio padre) {
        this();
        this.nombre = nombre;
        this.padre = padre;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
    public void setFechaCreacion(LocalDateTime fechaCreacion) { this.fechaCreacion = fechaCreacion; }

    public LocalDateTime getFechaModificacion() { return fechaModificacion; }
    public void setFechaModificacion(LocalDateTime fechaModificacion) { this.fechaModificacion = fechaModificacion; }

    public List<Archivo> getArchivos() { return archivos; }
    public void setArchivos(List<Archivo> archivos) { this.archivos = archivos; }

    public List<Directorio> getSubdirectorios() { return subdirectorios; }
    public void setSubdirectorios(List<Directorio> subdirectorios) { this.subdirectorios = subdirectorios; }

    public Directorio getPadre() { return padre; }
    public void setPadre(Directorio padre) { this.padre = padre; }

    public void agregarArchivo(Archivo archivo) {
        this.archivos.add(archivo);
        this.fechaModificacion = LocalDateTime.now();
    }

    public Directorio getSubdirectorioPorNombre(String nombre) {
        return subdirectorios.stream()
                .filter(d -> d.getNombre().equals(nombre))
                .findFirst()
                .orElse(null);
    }

    @JsonIgnore
    public long getTamanoTotal() {
        long total = archivos.stream().mapToLong(Archivo::getTamano).sum();
        for (Directorio sub : subdirectorios) {
            total += sub.getTamanoTotal();
        }
        return total;
    }
    public boolean eliminarArchivo(String nombre, String extension) {
        Iterator<Archivo> iterator = archivos.iterator();
        while (iterator.hasNext()) {
            Archivo archivo = iterator.next();
            if (archivo.getNombre().equals(nombre) && archivo.getExtension().equals(extension)) {
                iterator.remove();
                this.fechaModificacion = LocalDateTime.now();
                return true;
            }
        }

        // Intentar eliminar en subdirectorios
        for (Directorio sub : subdirectorios) {
            if (sub.eliminarArchivo(nombre, extension)) {
                this.fechaModificacion = LocalDateTime.now();
                return true;
            }
        }

        return false; // No encontrado
    }
    public boolean eliminarSubdirectorio(String path) {
        String[] partes = path.split("/", 2);
        String nombreActual = partes[0];

        for (Iterator<Directorio> it = subdirectorios.iterator(); it.hasNext(); ) {
            Directorio sub = it.next();
            if (sub.getNombre().equals(nombreActual)) {
                if (partes.length == 1) {
                    // Es el directorio a eliminar
                    it.remove();
                    this.fechaModificacion = LocalDateTime.now();
                    return true;
                } else {
                    // Profundizar en la ruta
                    boolean eliminado = sub.eliminarSubdirectorio(partes[1]);
                    if (eliminado) {
                        this.fechaModificacion = LocalDateTime.now();
                    }
                    return eliminado;
                }
            }
        }
        return false;
    }

}
