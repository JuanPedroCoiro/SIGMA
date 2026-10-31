import { useState, type FormEvent } from 'react'
import {
  obtenerProblemDetail,
  tipoProblema,
  TIPOS_PROBLEMA,
} from '../../../shared/api/problemDetail'
import { Modal } from '../../../shared/components/Modal'
import { useImportarInstalaciones } from '../hooks/useInstalaciones'
import type { ErrorFila } from '../types'
import './ImportarPlanillaDialogo.css'

type Props = {
  onCerrar: () => void
}

type Resultado =
  | { tipo: 'exito'; importadas: number }
  | { tipo: 'errores'; detalle: string; errores: ErrorFila[] }
  | { tipo: 'error'; mensaje: string }

const ERROR_GENERICO = 'No se pudo importar la planilla. Volvé a intentar en unos minutos.'

/** HU-02 (RF-29): la Jefatura sube la planilla CSV de instalaciones. */
export function ImportarPlanillaDialogo({ onCerrar }: Props) {
  const importar = useImportarInstalaciones()
  const [archivo, setArchivo] = useState<File | null>(null)
  const [resultado, setResultado] = useState<Resultado | null>(null)

  async function enviar(evento: FormEvent) {
    evento.preventDefault()
    if (!archivo) return
    setResultado(null)
    try {
      const { importadas } = await importar.mutateAsync(archivo)
      setResultado({ tipo: 'exito', importadas })
    } catch (error) {
      const problema = obtenerProblemDetail(error)
      if (tipoProblema(problema) === TIPOS_PROBLEMA.planillaInvalida) {
        const errores = Array.isArray(problema?.errores) ? (problema.errores as ErrorFila[]) : []
        setResultado({ tipo: 'errores', detalle: problema?.detail ?? '', errores })
      } else {
        setResultado({ tipo: 'error', mensaje: problema?.detail ?? ERROR_GENERICO })
      }
    }
  }

  if (resultado?.tipo === 'exito') {
    return (
      <Modal titulo="Importar planilla" onCerrar={onCerrar}>
        <p className="alerta alerta-exito" role="status">
          {resultado.importadas === 1
            ? 'Se importó 1 instalación.'
            : `Se importaron ${resultado.importadas} instalaciones.`}
        </p>
        <div className="modal-acciones">
          <button type="button" className="boton boton-primario" onClick={onCerrar}>
            Cerrar
          </button>
        </div>
      </Modal>
    )
  }

  return (
    <Modal titulo="Importar planilla" onCerrar={onCerrar}>
      <form className="importar-planilla" noValidate onSubmit={enviar}>
        <p>
          Elegí la planilla de instalaciones guardada como CSV, con las columnas codigo, nombre,
          ruta_zona, latitud y longitud. Si alguna fila tiene errores, no se importa ninguna.
        </p>
        <div className="campo">
          <label htmlFor="planilla-instalaciones">Planilla (.csv)</label>
          <input
            id="planilla-instalaciones"
            type="file"
            accept=".csv,text/csv"
            onChange={(evento) => {
              setArchivo(evento.target.files?.[0] ?? null)
              setResultado(null)
            }}
          />
        </div>

        {resultado?.tipo === 'error' && (
          <p className="alerta alerta-error" role="alert">
            {resultado.mensaje}
          </p>
        )}

        {resultado?.tipo === 'errores' && (
          <div className="importar-planilla-errores" role="alert">
            <p className="alerta alerta-error">{resultado.detalle}</p>
            <div className="importar-planilla-tabla">
              <table>
                <thead>
                  <tr>
                    <th scope="col">Fila</th>
                    <th scope="col">Campo</th>
                    <th scope="col">Mensaje</th>
                  </tr>
                </thead>
                <tbody>
                  {resultado.errores.map((error, i) => (
                    <tr key={i}>
                      <td>{error.fila}</td>
                      <td>{error.campo}</td>
                      <td>{error.mensaje}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}

        <div className="modal-acciones">
          <button type="button" className="boton boton-secundario" onClick={onCerrar}>
            Cancelar
          </button>
          <button
            type="submit"
            className="boton boton-primario"
            disabled={!archivo || importar.isPending}
          >
            {importar.isPending ? 'Importando...' : 'Importar'}
          </button>
        </div>
      </form>
    </Modal>
  )
}
