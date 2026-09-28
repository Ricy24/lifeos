# Financial Health Index (H)

El Índice de Salud Financiera ($H$) es una métrica continua que evalúa la estabilidad, liquidez y apalancamiento del usuario. El valor resultante se normaliza de 0 a 100.

## Fórmula Aprobada

$H = 100 \cdot (w_1 \cdot L + w_2 \cdot S + w_3 \cdot D)$

Donde las variables están limitadas (clamped) entre 0 y 1:
- **$L$ (Liquidity / Liquidez)**: $L = \text{clamp}\left(\frac{\text{meses\_de\_gasto\_cubiertos}}{6}, 0, 1\right)$
- **$S$ (Savings Rate / Tasa de Ahorro)**: $S = \text{clamp}\left(\frac{\text{tasa\_ahorro}}{0.20}, 0, 1\right)$
- **$D$ (Debt-to-Income / Apalancamiento)**: Sub-score invertido. $D = 1 - \text{clamp}\left(\frac{\text{pagos\_deuda} / \text{ingreso}}{0.36}, 0, 1\right)$

### Ponderaciones y Umbrales
- $w_1 = 0.4$ (Liquidez)
- $w_2 = 0.4$ (Ahorro)
- $w_3 = 0.2$ (Deuda)

*Nota: Los pesos son positivos, suman 1, dejando H en el rango [0, 100]. Los umbrales (6 meses, 20% ahorro, 36% deuda) y los pesos son configurables, con renormalización de los pesos si se añaden métricas futuras (ej. Estabilidad de Ingresos, Adherencia al Presupuesto).*

## Casos Límite
- Si no hay ingresos reportados o los datos son insuficientes para calcular las proporciones, la función devolverá `None` ("datos insuficientes") en lugar de 0, para no penalizar a usuarios nuevos.

## Implementación
Esta métrica se calculará e inyectará en la Fase 2b a través de los motores matemáticos del backend (Capa de Dominio -> Analytics).
