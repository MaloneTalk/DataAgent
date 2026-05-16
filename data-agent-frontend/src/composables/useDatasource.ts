import { ref } from "vue";
import {
  getDatasourceList,
  createDatasource,
  updateDatasource,
  deleteDatasource,
  type DatasourceRequest,
  type DatasourceResponse,
} from "@/api/datasource";

export function useDatasource() {
  const list = ref<DatasourceResponse[]>([]);
  const loading = ref(false);
  const error = ref<Error | null>(null);

  const fetchList = async () => {
    loading.value = true;
    error.value = null;
    try {
      const res = await getDatasourceList();
      list.value = res.data;
    } catch (e) {
      error.value = e as Error;
    } finally {
      loading.value = false;
    }
  };

  const addDatasource = async (data: DatasourceRequest) => {
    await createDatasource(data);
    await fetchList();
  };

  const editDatasource = async (data: DatasourceRequest) => {
    await updateDatasource(data);
    await fetchList();
  };

  const removeDatasource = async (id: number) => {
    await deleteDatasource(id);
    await fetchList();
  };

  return {
    list,
    loading,
    error,
    fetchList,
    addDatasource,
    editDatasource,
    removeDatasource,
  };
}
