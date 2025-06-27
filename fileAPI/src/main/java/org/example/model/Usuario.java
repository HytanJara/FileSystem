package org.example.model;

import java.util.UUID;

public class Usuario {
    private String id;
    private String nombre;
    private long espacioMaximo;
    private long espacioUsado;
    private Directorio directorioRaiz;
    private Directorio directorioCompartidos;

    public Usuario() {
        this.id = UUID.randomUUID().toString();
    }

    public Usuario(String nombre, long espacioMaximo) {
        this();
        this.nombre = nombre;
        this.espacioMaximo = espacioMaximo;
        this.espacioUsado = 0;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public long getEspacioMaximo() { return espacioMaximo; }
    public void setEspacioMaximo(long espacioMaximo) { this.espacioMaximo = espacioMaximo; }
    public long getEspacioUsado() { return espacioUsado; }
    public void setEspacioUsado(long espacioUsado) { this.espacioUsado = espacioUsado; }
    public Directorio getDirectorioRaiz() { return directorioRaiz; }
    public void setDirectorioRaiz(Directorio directorioRaiz) { this.directorioRaiz = directorioRaiz; }
    public Directorio getDirectorioCompartidos() { return directorioCompartidos; }
    public void setDirectorioCompartidos(Directorio directorioCompartidos) { this.directorioCompartidos = directorioCompartidos; }

    public void recalcularEspacioUsado() {
        long total = 0;
        if (directorioRaiz != null) total += directorioRaiz.getTamanoTotal();
        if (directorioCompartidos != null) total += directorioCompartidos.getTamanoTotal();
        this.espacioUsado = total;
    }

    public boolean puedeAgregarArchivo(long tamanoArchivo) {
        return (espacioUsado + tamanoArchivo) <= espacioMaximo;
    }
}
