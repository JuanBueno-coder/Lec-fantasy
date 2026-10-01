1. Actualizar localmente: git switch develop y luego git pull origin develop. Así se aseguran de tener lo último que subieron sus compañeros.
2. Crear la feature: git switch -c feature/nombre-de-la-tarea.
3. Trabajar y guardar: Hacer los cambios necesarios y guardarlos con git add . y git commit -m "Mensaje descriptivo".
4. Subir la rama: git push -u origin feature/nombre-de-la-tarea.
5. Abrir la PR: Ir a GitHub (o GitLab) y abrir una Pull Request desde su rama feature/* hacia la rama develop.
6. Revisión: Otro compañero del equipo debe revisar el código, dejar comentarios si es necesario y dar el visto bueno (Approve).
7. Fusión: Se hace el Merge de la PR y se elimina la rama feature remota para no acumular basura.
