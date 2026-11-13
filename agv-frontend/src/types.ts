/** 3D 场景里一台车的渲染数据。 */
export interface AgvView {
  id: string
  x: number
  y: number
  heading: number
  status: string
  taskId: number
  arcId?: string | null
}
