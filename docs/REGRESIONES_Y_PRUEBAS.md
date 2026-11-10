# Regresiones y validación

Base: V3.7.1 (`eb0c17e`), revisión 2026-11-09. **Código** significa inspección estática; **reportado** significa antecedente escrito/comentario del repositorio o encargo; **automatizado** exige resultado de ejecución en ENTREGA_WORK; **físico pendiente** no se puede dar por probado mediante compilación.

## Antecedentes y reglas

| Tema | Evidencia y regla que se conserva | Validación pendiente |
| --- | --- | --- |
| Contaminación por overlay | Código: OfferParser excluye `/h`, `/km`, resúmenes tarifa/min/km y textos del panel. Tests con entradas de fallos V3 | Capturas reales con overlay sobre carrusel y pantalla completa |
| Tarifa incorrecta | Código: exclusión de adicional/base dinámica/rating/viajes, tamaño/posición de tarifa y fallback sin símbolo. Test numérico 30.53 frente a importes secundarios | Cambios de tipografía, tema, tamaño, escala y diseño de DiDi |
| Reconstrucción de frames | Reportado en comentario OcrFrameAccumulator: experimento produjo cálculos transitorios erróneos con tarjetas móviles; código actual shim devuelve sólo el frame recibido, no usado por CaptureService. Test exige no combinar | Mantener prohibición; no introducir acumulación ni reconstrucción agresiva |
| Filas partidas | Código y test V33: `7min` y `(1.5km)` separados no se unen; parser exige filas completas | Carrusel en transición sin inventar ofertas |
| Propuesta falsa/transitoria | Código: plausibilidad más tres lecturas consecutivas estables para historial. El panel sigue inmediato. Test del tracker rompe secuencia ante cambio/reset | Tres lecturas estables pueden ser incorrectas: no hay garantía absoluta ni test OCR real |
| Mensaje inactivo/desaparición | Código: marcadores de inactividad limpian panel y candidato; fallo final reinicia candidato, limpia espera tras 3500 ms desde última oferta | Mensajes reales, desaparición y propuesta siguiente |
| Historial incorrecto | Código: confirmación algorítmica previa, deduplicación conservando primera lectura numérica y estadísticas por first_seen. Reportado V3.5.1 | SQLite real, reaparición tras 15 s, ventana de 10 min, cruce de día y cambio de oferta |
| Captura durante viaje | Código/comentarios V3.6.1–2: setup pausa análisis; comenzar detiene y libera proyección/reader/display; callbacks de OCR consultan pausa | Indicador del sistema detenido, navegación no cambia panel ni crea historial |
| Reanudación | Código: nueva autorización desde running/finished; cancelarla conserva temporizador/resumen; cancelar setup sólo quita pausa | Android 14/15 y fabricante del teléfono, cancelación/rechazo y autorización posterior |
| Círculo/expandido | Código V3.7: toque alterna sin reiniciar; tiempo monotónico, excedente y resumen final | Arrastre y toques rápidos, rotación y superposición sobre DiDi |
| Finalización accidental | Código: botón expandido requiere 900 ms; toque corto no finaliza; mover fuera o cancelar anula pulsación | Pulsar, soltar antes de umbral, deslizar fuera, mantener y comprobar resumen |
| Limpieza/recuperación | Código: removeAnyOverlay, limpieza en stop/onDestroy, aviso AnalysisResumeState por bloqueo/detención externa | Detener por botón/diana, permiso revocado, bloqueo/desbloqueo, reinicio de servicio/proceso; no prometer recuperación del timer en memoria |
| Datos locales | Código: SQLite v1 sin migración efectiva, preferencias privadas, borrado con confirmación UI; allowBackup habilitado | Copia/restauración autorizada, cancelación de borrado y conservación al actualizar con misma firma |

Los problemas con WindowManager y las reconstrucciones agresivas también son antecedentes **reportados por el usuario**; M01 no reprodujo esos fallos ni alteró esas estrategias.

## Suite existente

| Clase | Métodos @Test | Cobertura de entrada sintética |
| --- | ---: | --- |
| OfferParserTest | 5 | Tarifa dinámica/adicional, símbolo perdido, m/km, contaminación por overlay y siete ejemplos históricos en un método |
| OfferParserV33Test | 2 | Oferta estándar y rechazo de filas fragmentadas |
| HistoryCandidateTrackerTest | 4 | Tres lecturas, lectura transitoria, cambio de propuesta y reset |
| OcrFrameAccumulatorTest | 1 | Shim sin fusión |
| TripTimeEstimatorTest | 3 | Suma de minutos, medianoche y duraciones inválidas |
| **Total** | **15** | El resultado real se registra en ENTREGA_WORK |

No hay `src/androidTest` en la base inspeccionada. No hay pruebas dedicadas para ML Kit/MediaProjection, servicio Android, WindowManager, temporizador circular/protección de 900 ms, SQLite/deduplicación, preferencias, puntaje ni redondeo de tarifa mínima. No se agregaron pruebas que dupliquen implementación en M01.

## Guion físico para un encargo autorizado de validación

Registrar teléfono, Android, versión de DiDi, versión/hash/certificado de APK y hora del ensayo. No desinstalar la release ni instalar la debug como actualización. Usar dispositivo de prueba o un proceso de actualización con firma original expresamente autorizado, con respaldo de datos adecuado. Los pasos siguientes **no fueron ejecutados en M01**.

1. Autorizar overlay y captura por el diálogo oficial; cancelar y repetir; comprobar notificación de servicio y análisis local.
2. Mostrar ofertas conocidas completas y en carrusel; contrastar tarifa, ambos tramos, m/km, totales y $/h/$/km contra cálculo manual. Verificar que importes secundarios y propio overlay no se usen como ruta/tarifa.
3. Pasar tarjetas, dejar fragmentos y mostrar mensajes inactivos. Comprobar que no se persiste una lectura aislada y que se confirma únicamente la propuesta estable. Revisar deduplicación y primera fecha sin borrar datos.
4. Doble toque: comprobar setup, ajuste de minutos y cancelación. Comenzar: confirmar captura detenida y ausencia de OCR/registros durante navegación.
5. Alternar círculo/panel, arrastrar sin reiniciar, observar paso por 00:00 a excedente. Toque corto de finalizar no debe actuar; salida del botón cancela; mantener 900 ms finaliza y congela resumen.
6. Reanudar: cancelar permiso conserva estado; conceder permiso nuevo devuelve análisis sin overlays duplicados. Repetir desde resumen final.
7. Detener de forma explícita, por diana y desde el sistema; bloquear/desbloquear y comprobar aviso de reanudación. Registrar límites tras muerte de proceso sin asumir persistencia de timer.
8. Comprobar historial y configuración previos. No pulsar vaciar ni borrar registros del usuario. Una prueba destructiva requiere datos de ensayo y autorización independiente.

Para futuras modificaciones, escoger pruebas proporcionales al cambio y registrar fallos antes de repetir. Un `BUILD SUCCESSFUL` de assemble no acredita ejecución de tests ni comportamiento físico.
