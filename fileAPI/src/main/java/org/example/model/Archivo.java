package org.example.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.LocalDateTime;
import java.util.UUID;

public class Archivo {
    private String id;
    private String nombre;
    private String extension;
    private String contenido;
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaModificacion;
    private long tamano;

    public Archivo() {
        this.id = UUID.randomUUID().toString();
        this.fechaCreacion = LocalDateTime.now();
        this.fechaModificacion = LocalDateTime.now();
    }

    public Archivo(String nombre, String extension, String contenido) {
        this();
        this.nombre = nombre;
        this.extension = extension;
        setContenido(contenido); // Llama al setter que actualiza tamano y fecha
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getExtension() { return extension; }
    public void setExtension(String extension) { this.extension = extension; }
    public String getContenido() { return contenido; }

    public void setContenido(String contenido) {
        this.contenido = contenido;
        this.tamano = contenido != null ? contenido.getBytes().length : 0;
        this.fechaModificacion = LocalDateTime.now(); // Actualiza automáticamente
    }

    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
    public void setFechaCreacion(LocalDateTime fechaCreacion) { this.fechaCreacion = fechaCreacion; }
    public LocalDateTime getFechaModificacion() { return fechaModificacion; }
    public void setFechaModificacion(LocalDateTime fechaModificacion) { this.fechaModificacion = fechaModificacion; }
    public long getTamano() { return tamano; }
    public void setTamano(long tamano) { this.tamano = tamano; }

    @JsonIgnore
    public String getNombreCompleto() {
        return nombre + "." + extension;
    }
}
