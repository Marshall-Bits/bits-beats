# Bits beats Music app

<img alt="Portada youtube" height="600" src="./readme-assets/portada.png" width="800"/>

Puedes ver el vídeo del proceso de desarrollo en [YouTube](https://youtu.be/qT5bZPwZn8A).

## Descripción

Bits beats Music es una aplicación para Android que permite escuchar la música almacenada en el dispositivo. 
La aplicación está desarrollada en Kotlin.
Permite reproducir las canciones y generar playlists personalizadas.

## Github Copilot

Este proyecto ha sido desarrollado utilizando GitHub Copilot. Esto implica cierta discordancia en el estilo de código y la estructura del proyecto. Copilot con los modelos de GPT y Grok ha sugerido durante todo el proceso herramientas desactualizadas o poco eficientes, lo que ha requerido una revisión y corrección manual del código generado. Aunque esta revisión no se ha hecho de forma detallada.


## Problemas conocidos

- Gestión de permisos: No utiliza las últimas actualizaciones de gestión de permisos en Android.
- Compatibilidad limitada: Puede no ser compatible con todas las versiones de Android.
- Dispositivos no soportados: La aplicación puede no funcionar correctamente en algunos dispositivos.
- Buscador: La funcionalidad de búsqueda de canciones está implementada para buscar en metadatos, eso causa un bajo rendimiento ya que la búsqueda es demasiado compleja.
- Estadísticas: Las estadísticas son recogidas en un json local, pero su visualización es limitada.

