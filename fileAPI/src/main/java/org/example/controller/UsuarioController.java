package org.example.controller;

import org.example.model.Usuario;
import org.example.model.Directorio;
import org.example.util.JsonUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/usuarios")
public class UsuarioController {
    // Mapa en memoria solo para referencia rápida
    private static final Map<String, Usuario> usuarios = new HashMap<>();


    @PostMapping
    public ResponseEntity<?> crearUsuario(@RequestBody Map<String, Object> request) {
        String nombre = (String) request.get("nombre");
        long espacioMaximo = Long.parseLong(request.get("espacioMaximo").toString());

        try {
            // Verificar si ya existe un usuario con ese nombre
            Usuario existente = JsonUtil.buscarPorNombre(nombre);
            return ResponseEntity.status(409).body(Map.of("error", "Ya existe un usuario con ese nombre"));
        } catch (IOException e) {
            // No encontrado: se permite crear
        }

        Usuario usuario = new Usuario(nombre, espacioMaximo);
        Directorio root = new Directorio("root", null);
        Directorio compartidos = new Directorio("compartidos", null);
        usuario.setDirectorioRaiz(root);
        usuario.setDirectorioCompartidos(compartidos);

        try {
            JsonUtil.guardarUsuario(usuario);
            usuarios.put(usuario.getId(), usuario);
            return ResponseEntity.ok(usuario);
        } catch (IOException e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(Map.of("error", "Error al guardar el usuario"));
        }
    }


    // Login de usuario por nombre en la ruta
    @PostMapping("/login/{nombre}")
    public ResponseEntity<?> login(@PathVariable String nombre) {
        try {
            Usuario usuario = JsonUtil.buscarPorNombre(nombre);
            usuarios.put(usuario.getId(), usuario);
            return ResponseEntity.ok(usuario);
        } catch (IOException e) {
            return ResponseEntity.status(404).body(Map.of("error", "Usuario no encontrado"));
        }
    }



    // Endpoint para obtener usuario por ID
    @GetMapping("/{id}")
    public ResponseEntity<Usuario> getUsuario(@PathVariable String id) {
        try {
            Usuario usuario = JsonUtil.cargarUsuario(id);
            return ResponseEntity.ok(usuario);
        } catch (IOException e) {
            return ResponseEntity.notFound().build();
        }
    }
} 