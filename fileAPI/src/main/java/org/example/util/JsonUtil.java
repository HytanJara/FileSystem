package org.example.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.example.model.Usuario;

import java.io.File;
import java.io.IOException;

public class JsonUtil {
    private static final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(SerializationFeature.INDENT_OUTPUT);

    public static void guardarUsuario(Usuario usuario) throws IOException {
        String filename = "usuario_" + usuario.getId() + ".json";
        mapper.writeValue(new File(filename), usuario);
    }

    public static Usuario cargarUsuario(String id) throws IOException {
        String filename = "usuario_" + id + ".json";
        return mapper.readValue(new File(filename), Usuario.class);
    }
    public static Usuario buscarPorNombre(String nombre) throws IOException {
        File folder = new File(".");
        File[] archivos = folder.exists()
                ? folder.listFiles((dir, name) -> name.startsWith("usuario_") && name.endsWith(".json"))
                : new File[0];

        for (File archivo : archivos) {
            Usuario u = mapper.readValue(archivo, Usuario.class);
            if (u.getNombre().equals(nombre)) {
                return u;
            }
        }
        throw new IOException("Usuario no encontrado por nombre: " + nombre);
    }

} 