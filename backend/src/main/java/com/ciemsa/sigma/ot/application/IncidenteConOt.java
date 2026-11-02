package com.ciemsa.sigma.ot.application;

import com.ciemsa.sigma.ot.domain.Incidente;
import com.ciemsa.sigma.ot.domain.OrdenTrabajo;

/** Un incidente y su OT correctiva; {@code ordenTrabajo} es null si no tiene (4b). */
public record IncidenteConOt(Incidente incidente, OrdenTrabajo ordenTrabajo) {}
