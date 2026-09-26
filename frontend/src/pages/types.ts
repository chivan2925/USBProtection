import type { ReactNode } from 'react'
import type { ResourceState } from '../hooks/useResource'
import type { Snapshot } from '../services/models'
export interface PageProps { state: ResourceState<Snapshot>; refresh: () => void; refreshButton: ReactNode }
