<!--
 * Copyright (C) 2026 github.com/MaloneTalk
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 -->

<script setup lang="ts">
  import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue';
  import type { FormInstance, FormRules } from 'element-plus';
  import { ElMessage, ElMessageBox } from 'element-plus';
  import { useDatasource } from '@/composables/useDatasource';
  import type { DatasourceResponse } from '@/api/datasource';
  import {
    createLogicalRelation,
    deleteLogicalRelation,
    getColumnSemanticPage,
    getLogicalRelationPage,
    getRelationCandidateColumnPage,
    getRelationCandidateTablePage,
    getTableDomainOptions,
    getTableSemanticPage,
    resetColumnSemantic,
    resetTableSemantic,
    updateColumnSemantic,
    updateLogicalRelation,
    updateLogicalRelationEnabled,
    updateTableSemantic,
    type BindLogicalTableRelationRequest,
    type ColumnSemanticResponse,
    type LogicalTableRelationResponse,
    type RelationCandidateColumnResponse,
    type RelationCandidateTableResponse,
    type TableSemanticResponse,
    type UpdateLogicalTableRelationRequest,
  } from '@/api/semantic';
  import SemanticRelationWorkspace from './components/SemanticRelationWorkspace.vue';
  import type { RelationDragCreatePayload, RelationForm, TableNodeLayout } from './types';

  interface TableEditForm {
    datasourceId: number;
    tableName: string;
    domain: string;
    tableDescription: string;
    isVisible: boolean;
  }

  interface ColumnEditForm {
    columnName: string;
    columnDescription: string;
    isVisible: boolean;
  }

  const {
    list: datasourceList,
    loading: datasourceLoading,
    error: datasourceError,
    fetchList: fetchDatasourceList,
  } = useDatasource();

  const activeTab = ref<'semantic' | 'relation'>('semantic');

  const tableLoading = ref(false);
  const columnLoading = ref(false);
  const tableError = ref('');
  const columnError = ref('');

  const tableRows = ref<TableSemanticResponse[]>([]);
  const columnRows = ref<ColumnSemanticResponse[]>([]);

  const selectedDatasourceId = ref<number>();
  const keyword = ref('');
  const sortOrder = ref<'asc' | 'desc'>('asc');

  const tablePage = reactive({
    page: 1,
    pageSize: 10,
    total: 0,
  });

  const columnPage = reactive({
    page: 1,
    pageSize: 10,
    total: 0,
  });

  const selectedTable = ref<TableSemanticResponse | null>(null);
  const columnDrawerVisible = ref(false);

  const tableDialogVisible = ref(false);
  const tableSubmitLoading = ref(false);
  const tableFormRef = ref<FormInstance>();
  const domainDialogVisible = ref(false);
  const domainSubmitLoading = ref(false);
  const domainOptionsLoading = ref(false);
  const visibleDomainOptions = ref<Array<{ label: string; value: string }>>([]);
  const visibleDomains = ref<string[]>([]);
  const tableForm = reactive<TableEditForm>({
    datasourceId: 0,
    tableName: '',
    domain: '',
    tableDescription: '',
    isVisible: true,
  });

  const columnDialogVisible = ref(false);
  const columnSubmitLoading = ref(false);
  const columnFormRef = ref<FormInstance>();
  const columnForm = reactive<ColumnEditForm>({
    columnName: '',
    columnDescription: '',
    isVisible: true,
  });

  const tableRules: FormRules<TableEditForm> = {};
  const columnRules: FormRules<ColumnEditForm> = {};

  const relationLoading = ref(false);
  const relationError = ref('');
  const relationNodeLoading = ref(false);
  const relationNodes = ref<TableNodeLayout[]>([]);
  const relationRecords = ref<LogicalTableRelationResponse[]>([]);
  const selectedRelation = ref<LogicalTableRelationResponse | null>(null);
  const relationLoadToken = ref(0);

  const relationDialogVisible = ref(false);
  const relationSubmitLoading = ref(false);
  const relationFormRef = ref<FormInstance>();
  const relationForm = reactive<RelationForm>({
    sourceTableName: '',
    sourceColumnNames: [],
    targetTableName: '',
    targetColumnNames: [],
    description: '',
    enabled: true,
  });

  const relationRules: FormRules<RelationForm> = {
    sourceTableName: [{ required: true, message: '请选择源表', trigger: 'change' }],
    sourceColumnNames: [{ required: true, message: '请选择源列', trigger: 'change' }],
    targetTableName: [{ required: true, message: '请选择目标表', trigger: 'change' }],
    targetColumnNames: [{ required: true, message: '请选择目标列', trigger: 'change' }],
  };

  const relationSourceColumns = ref<RelationCandidateColumnResponse[]>([]);
  const relationTargetColumns = ref<RelationCandidateColumnResponse[]>([]);
  const suppressRelationTableWatch = ref(false);
  const BULK_FETCH_PAGE_SIZE = 100;

  const activeDatasource = computed<DatasourceResponse | undefined>(() =>
    datasourceList.value.find(item => item.id === selectedDatasourceId.value),
  );

  const canQuery = computed(() => typeof selectedDatasourceId.value === 'number');

  const draftRelation = computed(() => {
    if (
      !relationForm.sourceTableName ||
      !relationForm.targetTableName ||
      relationForm.sourceColumnNames.length === 0 ||
      relationForm.targetColumnNames.length === 0
    ) {
      return null;
    }

    return {
      sourceTableName: relationForm.sourceTableName,
      sourceColumnNames: [...relationForm.sourceColumnNames],
      targetTableName: relationForm.targetTableName,
      targetColumnNames: [...relationForm.targetColumnNames],
      enabled: relationForm.enabled,
    };
  });

  const normalizeOptionalText = (value: string) => {
    const trimmed = value.trim();
    return trimmed ? trimmed : null;
  };

  async function fetchAllPages<T>(
    loader: (
      page: number,
      pageSize: number,
    ) => Promise<{
      data: {
        data: {
          items: T[];
          totalPages: number;
        };
      };
    }>,
  ) {
    const items: T[] = [];
    let page = 1;
    let totalPages = 1;

    while (page <= totalPages) {
      const response = await loader(page, BULK_FETCH_PAGE_SIZE);
      const pageData = response.data.data;
      items.push(...pageData.items);
      totalPages = Math.max(pageData.totalPages, 1);
      page += 1;
    }

    return items;
  }

  const loadTablePage = async () => {
    if (!canQuery.value) {
      tableRows.value = [];
      tablePage.total = 0;
      tableError.value = '';
      return;
    }

    tableLoading.value = true;
    tableError.value = '';
    try {
      const response = await getTableSemanticPage({
        datasourceId: selectedDatasourceId.value,
        page: tablePage.page,
        pageSize: tablePage.pageSize,
        keywordPrefix: keyword.value.trim() || undefined,
        sortOrder: sortOrder.value,
      });
      const pageData = response.data.data;
      tableRows.value = pageData.items;
      tablePage.total = pageData.total;

      if (selectedTable.value) {
        const matched = pageData.items.find(
          item => item.tableName === selectedTable.value?.tableName,
        );
        if (matched) {
          selectedTable.value = matched;
        }
      }
    } catch (error) {
      tableError.value = (error as Error).message;
      tableRows.value = [];
      tablePage.total = 0;
    } finally {
      tableLoading.value = false;
    }
  };

  const loadColumnPage = async () => {
    if (!selectedTable.value || typeof selectedDatasourceId.value !== 'number') {
      columnRows.value = [];
      columnPage.total = 0;
      columnError.value = '';
      return;
    }

    columnLoading.value = true;
    columnError.value = '';
    try {
      const response = await getColumnSemanticPage({
        datasourceId: selectedDatasourceId.value,
        tableName: selectedTable.value.tableName,
        page: columnPage.page,
        pageSize: columnPage.pageSize,
        keywordPrefix: keyword.value.trim() || undefined,
        sortOrder: sortOrder.value,
      });
      const pageData = response.data.data;
      columnRows.value = pageData.items;
      columnPage.total = pageData.total;
    } catch (error) {
      columnError.value = (error as Error).message;
      columnRows.value = [];
      columnPage.total = 0;
    } finally {
      columnLoading.value = false;
    }
  };

  const buildRelationLayouts = (
    tables: RelationCandidateTableResponse[],
    columnsMap: Map<string, RelationCandidateColumnResponse[]>,
  ) => {
    const columnHeight = 32;
    const headerHeight = 58;
    const nodeWidth = 280;
    const gapX = 70;
    const gapY = 40;
    const columnsPerRow = 3;
    const rowHeights: number[] = [];

    const preparedNodes = tables.map((table, index) => {
      const rowIndex = Math.floor(index / columnsPerRow);
      const columnIndex = index % columnsPerRow;
      const columns = columnsMap.get(table.tableName) ?? [];
      const height = headerHeight + Math.max(columns.length, 1) * columnHeight + 20;
      rowHeights[rowIndex] = Math.max(rowHeights[rowIndex] ?? 0, height);
      return {
        table,
        rowIndex,
        columnIndex,
        columns,
        height,
      };
    });

    return preparedNodes.map(node => {
      const rowOffset = rowHeights
        .slice(0, node.rowIndex)
        .reduce((sum, height) => sum + height + gapY, 0);
      return {
        ...node.table,
        columns: node.columns,
        x: node.columnIndex * (nodeWidth + gapX),
        y: rowOffset,
        width: nodeWidth,
        height: node.height,
      } satisfies TableNodeLayout;
    });
  };

  const loadRelationNodes = async (
    datasourceId: number,
    loadToken: number,
  ): Promise<TableNodeLayout[]> => {
    relationNodeLoading.value = true;
    try {
      const tables = await fetchAllPages<RelationCandidateTableResponse>((page, pageSize) =>
        getRelationCandidateTablePage({
          datasourceId,
          page,
          pageSize,
          sortOrder: 'asc',
        }),
      );
      const columnResponses = await Promise.all(
        tables.map(table =>
          fetchAllPages<RelationCandidateColumnResponse>((page, pageSize) =>
            getRelationCandidateColumnPage({
              datasourceId,
              tableName: table.tableName,
              page,
              pageSize,
              sortOrder: 'asc',
            }),
          ),
        ),
      );

      const columnsMap = new Map<string, RelationCandidateColumnResponse[]>();
      tables.forEach((table, index) => {
        columnsMap.set(table.tableName, columnResponses[index]);
      });

      const nextNodes = buildRelationLayouts(tables, columnsMap);
      if (loadToken !== relationLoadToken.value || datasourceId !== selectedDatasourceId.value) {
        return nextNodes;
      }
      relationNodes.value = nextNodes;
      await nextTick();
      return nextNodes;
    } finally {
      if (loadToken === relationLoadToken.value) {
        relationNodeLoading.value = false;
      }
    }
  };

  const loadAllRelations = async (
    datasourceId: number,
    nodes: TableNodeLayout[],
    loadToken: number,
  ) => {
    relationLoading.value = true;
    relationError.value = '';
    try {
      const responses = await Promise.all(
        nodes.map(node =>
          fetchAllPages<LogicalTableRelationResponse>((page, pageSize) =>
            getLogicalRelationPage({
              datasourceId,
              tableName: node.tableName,
              page,
              pageSize,
              sortOrder: 'desc',
            }),
          ),
        ),
      );
      if (loadToken !== relationLoadToken.value || datasourceId !== selectedDatasourceId.value) {
        return;
      }
      relationRecords.value = responses.flatMap(items => items);
    } catch (error) {
      if (loadToken !== relationLoadToken.value || datasourceId !== selectedDatasourceId.value) {
        return;
      }
      relationError.value = (error as Error).message;
      relationRecords.value = [];
    } finally {
      if (loadToken === relationLoadToken.value) {
        relationLoading.value = false;
      }
    }
  };

  const initializeDatasource = async () => {
    await fetchDatasourceList();
    if (typeof selectedDatasourceId.value !== 'number') {
      const firstActive = datasourceList.value.find(item => item.status === 'ACTIVE');
      selectedDatasourceId.value = firstActive?.id ?? datasourceList.value[0]?.id;
    }
    await loadTablePage();
    if (activeTab.value === 'relation') {
      await ensureRelationTabLoaded();
    }
  };

  const ensureRelationTabLoaded = async () => {
    if (!canQuery.value || typeof selectedDatasourceId.value !== 'number') {
      return;
    }

    const datasourceId = selectedDatasourceId.value;
    const loadToken = relationLoadToken.value + 1;
    relationLoadToken.value = loadToken;

    try {
      const nodes = await loadRelationNodes(datasourceId, loadToken);
      if (loadToken !== relationLoadToken.value || datasourceId !== selectedDatasourceId.value) {
        return;
      }
      await loadAllRelations(datasourceId, nodes, loadToken);
    } catch (error) {
      if (loadToken !== relationLoadToken.value || datasourceId !== selectedDatasourceId.value) {
        return;
      }
      relationError.value = (error as Error).message;
      relationNodes.value = [];
      relationRecords.value = [];
      relationNodeLoading.value = false;
      relationLoading.value = false;
    }
  };

  onMounted(() => {
    void initializeDatasource();
  });

  watch(selectedDatasourceId, async () => {
    tablePage.page = 1;
    columnPage.page = 1;
    selectedTable.value = null;
    columnDrawerVisible.value = false;
    relationRecords.value = [];
    relationNodes.value = [];
    resetRelationForm();
    await loadTablePage();
    if (activeTab.value === 'relation') {
      await ensureRelationTabLoaded();
    }
  });

  watch(activeTab, async tab => {
    if (tab === 'relation') {
      await ensureRelationTabLoaded();
    }
  });

  watch(
    () => relationForm.sourceTableName,
    async tableName => {
      if (suppressRelationTableWatch.value) {
        return;
      }
      relationForm.sourceColumnNames = [];
      if (!tableName || typeof selectedDatasourceId.value !== 'number') {
        relationSourceColumns.value = [];
        return;
      }
      relationSourceColumns.value = await fetchRelationColumns(tableName);
    },
  );

  watch(
    () => relationForm.targetTableName,
    async tableName => {
      if (suppressRelationTableWatch.value) {
        return;
      }
      relationForm.targetColumnNames = [];
      if (!tableName || typeof selectedDatasourceId.value !== 'number') {
        relationTargetColumns.value = [];
        return;
      }
      relationTargetColumns.value = await fetchRelationColumns(tableName);
    },
  );

  const handleSearch = async () => {
    tablePage.page = 1;
    columnPage.page = 1;
    await loadTablePage();
    if (columnDrawerVisible.value) {
      await loadColumnPage();
    }
  };

  const handleTablePageChange = async (page: number) => {
    tablePage.page = page;
    await loadTablePage();
  };

  const handleTableSizeChange = async (pageSize: number) => {
    tablePage.pageSize = pageSize;
    tablePage.page = 1;
    await loadTablePage();
  };

  const handleColumnPageChange = async (page: number) => {
    columnPage.page = page;
    await loadColumnPage();
  };

  const handleColumnSizeChange = async (pageSize: number) => {
    columnPage.pageSize = pageSize;
    columnPage.page = 1;
    await loadColumnPage();
  };

  const handleViewColumns = async (row: TableSemanticResponse) => {
    if (!row.hasPhysicalTable) {
      ElMessage.warning('当前表对应的物理表已不存在，无法查看列，但仍可编辑或重置语义配置');
      return;
    }
    selectedTable.value = row;
    columnDrawerVisible.value = true;
    columnPage.page = 1;
    await loadColumnPage();
  };

  const handleOpenTableEdit = (row: TableSemanticResponse) => {
    if (typeof selectedDatasourceId.value !== 'number') {
      return;
    }

    Object.assign(tableForm, {
      datasourceId: selectedDatasourceId.value,
      tableName: row.tableName,
      domain: row.domain ?? '',
      tableDescription: row.tableDescription ?? '',
      isVisible: row.isVisible,
    });
    tableDialogVisible.value = true;
  };

  const handleOpenColumnEdit = (row: ColumnSemanticResponse) => {
    Object.assign(columnForm, {
      columnName: row.columnName,
      columnDescription: row.columnDescription ?? '',
      isVisible: row.isVisible,
    });
    columnDialogVisible.value = true;
  };

  const handleOpenVisibleDomainDialog = async () => {
    if (typeof selectedDatasourceId.value !== 'number') {
      ElMessage.warning('请先选择数据源');
      return;
    }

    domainDialogVisible.value = true;
    domainOptionsLoading.value = true;
    visibleDomainOptions.value = [];
    visibleDomains.value = [];

    try {
      const [domainRes, tableRes] = await Promise.all([
        getTableDomainOptions(selectedDatasourceId.value),
        fetchAllPages<TableSemanticResponse>((page, pageSize) =>
          getTableSemanticPage({
            datasourceId: selectedDatasourceId.value,
            page,
            pageSize,
            sortOrder: 'asc',
          }),
        ),
      ]);

      const domainValues = domainRes.data.data ?? [];
      visibleDomainOptions.value = domainValues.map(domain => ({
        value: domain,
        label: domain.trim() ? domain : '未设置业务域',
      }));

      visibleDomains.value = domainValues.filter(domain =>
        tableRes.some(row => (row.domain ?? '') === domain && row.isVisible),
      );
    } finally {
      domainOptionsLoading.value = false;
    }
  };

  const handleSubmitVisibleDomains = async () => {
    if (typeof selectedDatasourceId.value !== 'number') {
      return;
    }

    domainSubmitLoading.value = true;
    try {
      const allTables = await fetchAllPages<TableSemanticResponse>((page, pageSize) =>
        getTableSemanticPage({
          datasourceId: selectedDatasourceId.value,
          page,
          pageSize,
          sortOrder: 'asc',
        }),
      );

      const visibleDomainSet = new Set(visibleDomains.value);
      await Promise.all(
        allTables.map(row =>
          updateTableSemantic({
            datasourceId: selectedDatasourceId.value as number,
            tableName: row.tableName,
            domain: row.domain,
            tableDescription: row.tableDescription,
            isVisible: visibleDomainSet.has(row.domain ?? ''),
          }),
        ),
      );

      const refreshedSelectedTable = selectedTable.value
        ? (allTables.find(item => item.tableName === selectedTable.value?.tableName) ?? null)
        : null;

      ElMessage.success('可见域已更新');
      domainDialogVisible.value = false;
      await loadTablePage();
      if (selectedTable.value) {
        selectedTable.value = refreshedSelectedTable;
      }
      if (columnDrawerVisible.value && refreshedSelectedTable) {
        await loadColumnPage();
      }
      if (activeTab.value === 'relation') {
        await ensureRelationTabLoaded();
      }
    } finally {
      domainSubmitLoading.value = false;
    }
  };

  const handleSubmitTable = async () => {
    if (!tableFormRef.value) {
      return;
    }

    const valid = await tableFormRef.value.validate().catch(() => false);
    if (!valid) {
      return;
    }

    tableSubmitLoading.value = true;
    try {
      await updateTableSemantic({
        datasourceId: tableForm.datasourceId,
        tableName: tableForm.tableName,
        domain: normalizeOptionalText(tableForm.domain),
        tableDescription: normalizeOptionalText(tableForm.tableDescription),
        isVisible: tableForm.isVisible,
      });
      ElMessage.success('表语义已更新');
      tableDialogVisible.value = false;
      await loadTablePage();
      if (selectedTable.value?.tableName === tableForm.tableName && columnDrawerVisible.value) {
        selectedTable.value =
          tableRows.value.find(item => item.tableName === tableForm.tableName) ?? null;
      }
    } finally {
      tableSubmitLoading.value = false;
    }
  };

  const handleSubmitColumn = async () => {
    if (
      !columnFormRef.value ||
      !selectedTable.value ||
      typeof selectedDatasourceId.value !== 'number'
    ) {
      return;
    }

    const valid = await columnFormRef.value.validate().catch(() => false);
    if (!valid) {
      return;
    }

    columnSubmitLoading.value = true;
    try {
      await updateColumnSemantic(selectedDatasourceId.value, selectedTable.value.tableName, {
        columnName: columnForm.columnName,
        columnDescription: normalizeOptionalText(columnForm.columnDescription),
        isVisible: columnForm.isVisible,
      });
      ElMessage.success('列语义已更新');
      columnDialogVisible.value = false;
      await loadColumnPage();
    } finally {
      columnSubmitLoading.value = false;
    }
  };

  const handleResetTable = async (row: TableSemanticResponse) => {
    if (typeof selectedDatasourceId.value !== 'number') {
      return;
    }

    try {
      await ElMessageBox.confirm(`确定要重置表 ${row.tableName} 的语义配置吗？`, '提示', {
        type: 'warning',
        confirmButtonText: '确定',
        cancelButtonText: '取消',
      });
      await resetTableSemantic(selectedDatasourceId.value, row.tableName);
      ElMessage.success('表语义已重置');
      if (selectedTable.value?.tableName === row.tableName) {
        selectedTable.value = null;
        columnDrawerVisible.value = false;
      }
      await loadTablePage();
    } catch {
      // ignore cancel
    }
  };

  const handleResetColumn = async (row: ColumnSemanticResponse) => {
    if (typeof selectedDatasourceId.value !== 'number' || !selectedTable.value) {
      return;
    }

    try {
      await ElMessageBox.confirm(
        `确定要重置列 ${selectedTable.value.tableName}.${row.columnName} 的语义配置吗？`,
        '提示',
        {
          type: 'warning',
          confirmButtonText: '确定',
          cancelButtonText: '取消',
        },
      );
      await resetColumnSemantic(
        selectedDatasourceId.value,
        selectedTable.value.tableName,
        row.columnName,
      );
      ElMessage.success('列语义已重置');
      await loadColumnPage();
    } catch {
      // ignore cancel
    }
  };

  const resetRelationForm = () => {
    Object.assign(relationForm, {
      sourceTableName: '',
      sourceColumnNames: [],
      targetTableName: '',
      targetColumnNames: [],
      description: '',
      enabled: true,
    });
    relationSourceColumns.value = [];
    relationTargetColumns.value = [];
    selectedRelation.value = null;
  };

  const fetchRelationColumns = async (tableName: string) => {
    if (typeof selectedDatasourceId.value !== 'number') {
      return [];
    }
    return fetchAllPages<RelationCandidateColumnResponse>((page, pageSize) =>
      getRelationCandidateColumnPage({
        datasourceId: selectedDatasourceId.value as number,
        tableName,
        page,
        pageSize,
        sortOrder: 'asc',
      }),
    );
  };

  const handleDragCreateRelation = async (payload: RelationDragCreatePayload) => {
    if (payload.sourceTableName === payload.targetTableName) {
      ElMessage.warning('不能把关系拖回同一张表');
      return;
    }

    suppressRelationTableWatch.value = true;
    try {
      Object.assign(relationForm, {
        sourceTableName: payload.sourceTableName,
        sourceColumnNames: [payload.sourceColumnName],
        targetTableName: payload.targetTableName,
        targetColumnNames: [payload.targetColumnName],
        description: '',
        enabled: true,
      });

      const [sourceColumns, targetColumns] = await Promise.all([
        fetchRelationColumns(payload.sourceTableName),
        fetchRelationColumns(payload.targetTableName),
      ]);
      relationSourceColumns.value = sourceColumns;
      relationTargetColumns.value = targetColumns;
      relationForm.sourceColumnNames = [payload.sourceColumnName];
      relationForm.targetColumnNames = [payload.targetColumnName];
      selectedRelation.value = null;
      relationDialogVisible.value = true;
    } finally {
      suppressRelationTableWatch.value = false;
    }
  };

  const handleEditRelation = async (relation: LogicalTableRelationResponse) => {
    if (relation.source === 'physical') {
      ElMessage.warning('物理外键仅展示，不支持直接编辑');
      return;
    }
    if (typeof relation.id !== 'number') {
      ElMessage.error('当前逻辑外键缺少有效标识，无法编辑');
      return;
    }
    selectedRelation.value = relation;
    relationDialogVisible.value = true;
    suppressRelationTableWatch.value = true;
    try {
      Object.assign(relationForm, {
        sourceTableName: relation.sourceTableName,
        sourceColumnNames: [...relation.sourceColumnNames],
        targetTableName: relation.targetTableName,
        targetColumnNames: [...relation.targetColumnNames],
        description: relation.description ?? '',
        enabled: relation.enabled,
      });
      const [sourceColumns, targetColumns] = await Promise.all([
        fetchRelationColumns(relation.sourceTableName),
        fetchRelationColumns(relation.targetTableName),
      ]);
      relationSourceColumns.value = sourceColumns;
      relationTargetColumns.value = targetColumns;
      relationForm.sourceColumnNames = [...relation.sourceColumnNames];
      relationForm.targetColumnNames = [...relation.targetColumnNames];
    } finally {
      suppressRelationTableWatch.value = false;
    }
  };

  const handleSubmitRelation = async () => {
    if (!relationFormRef.value || typeof selectedDatasourceId.value !== 'number') {
      return;
    }
    const valid = await relationFormRef.value.validate().catch(() => false);
    if (!valid) {
      return;
    }

    if (relationForm.sourceTableName === relationForm.targetTableName) {
      ElMessage.warning('源表和目标表不能相同');
      return;
    }

    if (relationForm.sourceColumnNames.length !== relationForm.targetColumnNames.length) {
      ElMessage.warning('源列与目标列数量必须一致');
      return;
    }

    relationSubmitLoading.value = true;
    try {
      const payload: BindLogicalTableRelationRequest | UpdateLogicalTableRelationRequest = {
        sourceColumnNames: [...relationForm.sourceColumnNames],
        targetTableName: relationForm.targetTableName,
        targetColumnNames: [...relationForm.targetColumnNames],
        description: relationForm.description.trim(),
        enabled: relationForm.enabled,
      };

      if (selectedRelation.value) {
        const selectedRelationId = selectedRelation.value.id;
        if (typeof selectedRelationId !== 'number') {
          ElMessage.error('当前逻辑外键缺少有效标识，无法更新');
          return;
        }
        await updateLogicalRelation(
          selectedDatasourceId.value,
          relationForm.sourceTableName,
          selectedRelationId,
          payload,
        );
        ElMessage.success('逻辑外键已更新');
      } else {
        await createLogicalRelation(
          selectedDatasourceId.value,
          relationForm.sourceTableName,
          payload,
        );
        ElMessage.success('逻辑外键已创建');
      }

      relationDialogVisible.value = false;
      resetRelationForm();
      await ensureRelationTabLoaded();
    } finally {
      relationSubmitLoading.value = false;
    }
  };

  const handleDeleteRelation = async (relation: LogicalTableRelationResponse) => {
    if (relation.source === 'physical') {
      ElMessage.warning('物理外键仅展示，不支持删除');
      return;
    }
    if (typeof relation.id !== 'number') {
      ElMessage.error('当前逻辑外键缺少有效标识，无法删除');
      return;
    }
    if (typeof selectedDatasourceId.value !== 'number') {
      return;
    }
    try {
      await ElMessageBox.confirm(
        `确定要删除逻辑外键 ${relation.sourceTableName} -> ${relation.targetTableName} 吗？`,
        '提示',
        {
          type: 'warning',
          confirmButtonText: '确定',
          cancelButtonText: '取消',
        },
      );
      await deleteLogicalRelation(
        selectedDatasourceId.value,
        relation.sourceTableName,
        relation.id,
      );
      ElMessage.success('逻辑外键已删除');
      await ensureRelationTabLoaded();
    } catch {
      // ignore cancel
    }
  };

  const handleToggleRelationEnabled = async (
    relation: LogicalTableRelationResponse,
    value: boolean,
  ) => {
    if (relation.source === 'physical') {
      ElMessage.warning('物理外键始终由数据库结构决定，不能在这里启停');
      return;
    }
    if (typeof relation.id !== 'number') {
      ElMessage.error('当前逻辑外键缺少有效标识，无法更新状态');
      return;
    }
    if (typeof selectedDatasourceId.value !== 'number') {
      return;
    }
    await updateLogicalRelationEnabled(
      selectedDatasourceId.value,
      relation.sourceTableName,
      relation.id,
      {
        enabled: value,
      },
    );
    relation.enabled = value;
    ElMessage.success(value ? '逻辑外键已启用' : '逻辑外键已禁用');
    await ensureRelationTabLoaded();
  };

  const formatTime = (value: string | null) => {
    if (!value) {
      return '-';
    }
    return value.replace('T', ' ');
  };

  const visibilityTagType = (visible: boolean) => (visible ? 'success' : 'info');
  const semanticStateTagType = (effective: boolean) => (effective ? 'success' : 'danger');
  const semanticStateLabel = (effective: boolean) => (effective ? '生效中' : '失效');
</script>

<template>
  <div class="semantic-model-page">
    <section class="hero-card">
      <div>
        <h2 class="hero-title">语义模型管理</h2>
        <p class="hero-desc">统一维护表语义、列语义以及逻辑外键，并通过可视化关系图辅助配置。</p>
      </div>
      <div class="hero-meta">
        <span>当前数据源</span>
        <strong>{{ activeDatasource?.name ?? '未选择' }}</strong>
      </div>
    </section>

    <section class="toolbar-card">
      <div class="toolbar-grid">
        <el-select
          v-model="selectedDatasourceId"
          class="toolbar-field"
          filterable
          placeholder="选择数据源"
          :loading="datasourceLoading"
        >
          <el-option
            v-for="item in datasourceList"
            :key="item.id"
            :label="`${item.name} (${item.type})`"
            :value="item.id"
          />
        </el-select>
        <el-input
          v-model="keyword"
          class="toolbar-field"
          clearable
          placeholder="按表名或列名前缀搜索"
          @keyup.enter="handleSearch"
        />
        <el-segmented
          v-model="sortOrder"
          :options="[
            { label: '名称升序', value: 'asc' },
            { label: '名称降序', value: 'desc' },
          ]"
          @change="handleSearch"
        />
        <div class="toolbar-actions">
          <el-button type="primary" @click="handleSearch">查询</el-button>
        </div>
      </div>
      <div v-if="datasourceError" class="error-tip">
        数据源加载失败：{{ datasourceError.message }}
      </div>
    </section>

    <section class="content-card">
      <div v-if="activeTab === 'semantic'" class="content-toolbar">
        <div>
          <h3 class="content-toolbar-title">语义模型</h3>
          <p class="content-toolbar-desc">按业务域批量控制表可见性，默认全部开启。</p>
        </div>
        <div class="content-toolbar-actions">
          <el-button plain @click="handleOpenVisibleDomainDialog">选择可见域</el-button>
          <el-tag type="primary" effect="plain">共 {{ tablePage.total }} 张表</el-tag>
        </div>
      </div>
      <el-tabs v-model="activeTab" class="semantic-tabs">
        <el-tab-pane label="表与列语义" name="semantic">
          <section class="table-panel">
            <div class="section-header">
              <div>
                <h3>表语义列表</h3>
                <p>维护业务域、表描述以及表级可见性。</p>
              </div>
            </div>

            <el-table v-loading="tableLoading" :data="tableRows" class="semantic-table">
              <el-table-column prop="tableName" label="表名" min-width="220" />
              <el-table-column prop="domain" label="业务域" min-width="160">
                <template #default="{ row }">
                  {{ row.domain || '-' }}
                </template>
              </el-table-column>
              <el-table-column
                prop="physicalTableDescription"
                label="物理描述"
                min-width="240"
                show-overflow-tooltip
              >
                <template #default="{ row }">
                  {{ row.physicalTableDescription || '-' }}
                </template>
              </el-table-column>
              <el-table-column
                prop="tableDescription"
                label="语义描述"
                min-width="260"
                show-overflow-tooltip
              >
                <template #default="{ row }">
                  {{ row.tableDescription || '-' }}
                </template>
              </el-table-column>
              <el-table-column label="可见性" width="110">
                <template #default="{ row }">
                  <el-tag :type="visibilityTagType(row.isVisible)">
                    {{ row.isVisible ? '显示' : '隐藏' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="状态" width="120">
                <template #default="{ row }">
                  <el-tag :type="semanticStateTagType(row.effective)">
                    {{ semanticStateLabel(row.effective) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="更新时间" width="180">
                <template #default="{ row }">
                  {{ formatTime(row.updateTime) }}
                </template>
              </el-table-column>
              <el-table-column label="操作" width="280" fixed="right">
                <template #default="{ row }">
                  <el-button link type="primary" @click="handleViewColumns(row)">查看列</el-button>
                  <el-button link type="primary" @click="handleOpenTableEdit(row)">编辑</el-button>
                  <el-button link type="danger" @click="handleResetTable(row)">重置</el-button>
                </template>
              </el-table-column>
            </el-table>

            <div v-if="tableError" class="error-tip">表语义加载失败：{{ tableError }}</div>
            <div
              v-for="row in tableRows.filter(item => item.invalidReason)"
              :key="`table-invalid-${row.tableName}`"
              class="error-tip"
            >
              {{ row.tableName }}：{{ row.invalidReason }}
            </div>

            <div class="pagination-wrap">
              <el-pagination
                background
                layout="total, sizes, prev, pager, next"
                :current-page="tablePage.page"
                :page-size="tablePage.pageSize"
                :page-sizes="[10, 20, 50]"
                :total="tablePage.total"
                @current-change="handleTablePageChange"
                @size-change="handleTableSizeChange"
              />
            </div>
          </section>
        </el-tab-pane>

        <el-tab-pane label="逻辑外键" name="relation">
          <SemanticRelationWorkspace
            :datasource-id="selectedDatasourceId"
            :active="activeTab === 'relation'"
            :loading="relationLoading"
            :node-loading="relationNodeLoading"
            :relation-error="relationError"
            :nodes="relationNodes"
            :relations="relationRecords"
            :draft-relation="draftRelation"
            @refresh="ensureRelationTabLoaded"
            @edit-relation="handleEditRelation"
            @delete-relation="handleDeleteRelation"
            @toggle-relation-enabled="handleToggleRelationEnabled"
            @drag-create-relation="handleDragCreateRelation"
          />
        </el-tab-pane>
      </el-tabs>
    </section>

    <el-drawer
      v-model="columnDrawerVisible"
      :title="selectedTable ? `${selectedTable.tableName} 的列语义` : '列语义'"
      size="60%"
    >
      <div v-if="selectedTable" class="drawer-summary">
        <div class="summary-item">
          <span>业务域</span>
          <strong>{{ selectedTable.domain || '-' }}</strong>
        </div>
        <div class="summary-item">
          <span>表可见性</span>
          <el-tag :type="visibilityTagType(selectedTable.isVisible)">
            {{ selectedTable.isVisible ? '显示' : '隐藏' }}
          </el-tag>
        </div>
        <div class="summary-item">
          <span>状态</span>
          <el-tag :type="semanticStateTagType(selectedTable.effective)">
            {{ semanticStateLabel(selectedTable.effective) }}
          </el-tag>
        </div>
      </div>

      <el-table v-loading="columnLoading" :data="columnRows" class="semantic-table">
        <el-table-column prop="columnName" label="列名" min-width="180" />
        <el-table-column prop="typeName" label="类型" width="140">
          <template #default="{ row }">
            {{ row.typeName || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="主键" width="90">
          <template #default="{ row }">
            <el-tag :type="row.primaryKey ? 'warning' : 'info'" effect="plain">
              {{ row.primaryKey ? '是' : '否' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          prop="physicalColumnDescription"
          label="物理描述"
          min-width="220"
          show-overflow-tooltip
        >
          <template #default="{ row }">
            {{ row.physicalColumnDescription || '-' }}
          </template>
        </el-table-column>
        <el-table-column
          prop="columnDescription"
          label="语义描述"
          min-width="220"
          show-overflow-tooltip
        >
          <template #default="{ row }">
            {{ row.columnDescription || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="可见性" width="100">
          <template #default="{ row }">
            <el-tag :type="visibilityTagType(row.isVisible)">
              {{ row.isVisible ? '显示' : '隐藏' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="semanticStateTagType(row.effective)">
              {{ semanticStateLabel(row.effective) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="更新时间" width="180">
          <template #default="{ row }">
            {{ formatTime(row.updateTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleOpenColumnEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="handleResetColumn(row)">重置</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div v-if="columnError" class="error-tip">列语义加载失败：{{ columnError }}</div>
      <div
        v-for="row in columnRows.filter(item => item.invalidReason)"
        :key="`column-invalid-${row.columnName}`"
        class="error-tip"
      >
        {{ row.columnName }}：{{ row.invalidReason }}
      </div>

      <div class="pagination-wrap">
        <el-pagination
          background
          layout="total, sizes, prev, pager, next"
          :current-page="columnPage.page"
          :page-size="columnPage.pageSize"
          :page-sizes="[10, 20, 50]"
          :total="columnPage.total"
          @current-change="handleColumnPageChange"
          @size-change="handleColumnSizeChange"
        />
      </div>
    </el-drawer>

    <el-dialog v-model="tableDialogVisible" title="编辑表语义" width="640px">
      <el-form ref="tableFormRef" :model="tableForm" :rules="tableRules" label-width="110px">
        <el-form-item label="表名">
          <el-input :model-value="tableForm.tableName" disabled />
        </el-form-item>
        <el-form-item label="业务域" prop="domain">
          <el-input v-model="tableForm.domain" placeholder="例如：会员、订单、商品" />
        </el-form-item>
        <el-form-item label="语义描述" prop="tableDescription">
          <el-input
            v-model="tableForm.tableDescription"
            type="textarea"
            :rows="4"
            placeholder="请输入更贴近业务含义的表描述"
          />
        </el-form-item>
        <el-form-item label="是否可见">
          <el-switch v-model="tableForm.isVisible" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="tableDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="tableSubmitLoading" @click="handleSubmitTable">
          保存
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="domainDialogVisible" title="选择可见域" width="520px">
      <div class="domain-dialog-body">
        <p class="domain-dialog-tip">
          未选中的业务域会将对应表的可见性批量设置为隐藏，默认全开启。
        </p>
        <el-select
          v-model="visibleDomains"
          multiple
          filterable
          clearable
          collapse-tags
          collapse-tags-tooltip
          :max-collapse-tags="2"
          :loading="domainOptionsLoading"
          placeholder="请选择可见的业务域"
          style="width: 100%"
        >
          <el-option
            v-for="item in visibleDomainOptions"
            :key="item.value || '__EMPTY__'"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </div>
      <template #footer>
        <el-button @click="domainDialogVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="domainSubmitLoading"
          @click="handleSubmitVisibleDomains"
        >
          保存
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="columnDialogVisible" title="编辑列语义" width="640px">
      <el-form ref="columnFormRef" :model="columnForm" :rules="columnRules" label-width="110px">
        <el-form-item label="列名">
          <el-input :model-value="columnForm.columnName" disabled />
        </el-form-item>
        <el-form-item label="语义描述" prop="columnDescription">
          <el-input
            v-model="columnForm.columnDescription"
            type="textarea"
            :rows="4"
            placeholder="请输入列的业务含义、口径或使用说明"
          />
        </el-form-item>
        <el-form-item label="是否可见">
          <el-switch v-model="columnForm.isVisible" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="columnDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="columnSubmitLoading" @click="handleSubmitColumn">
          保存
        </el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="relationDialogVisible"
      :title="selectedRelation ? '编辑逻辑外键' : '新增逻辑外键'"
      width="720px"
    >
      <el-form
        ref="relationFormRef"
        :model="relationForm"
        :rules="relationRules"
        label-width="120px"
      >
        <el-form-item label="源表" prop="sourceTableName">
          <el-select v-model="relationForm.sourceTableName" filterable placeholder="选择源表">
            <el-option
              v-for="node in relationNodes"
              :key="node.tableName"
              :label="node.tableName"
              :value="node.tableName"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="源列" prop="sourceColumnNames">
          <el-select
            v-model="relationForm.sourceColumnNames"
            multiple
            collapse-tags
            collapse-tags-tooltip
            placeholder="选择一个或多个源列"
          >
            <el-option
              v-for="column in relationSourceColumns"
              :key="column.columnName"
              :label="`${column.columnName} (${column.typeName || 'UNKNOWN'})`"
              :value="column.columnName"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="目标表" prop="targetTableName">
          <el-select v-model="relationForm.targetTableName" filterable placeholder="选择目标表">
            <el-option
              v-for="node in relationNodes"
              :key="node.tableName"
              :label="node.tableName"
              :value="node.tableName"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="目标列" prop="targetColumnNames">
          <el-select
            v-model="relationForm.targetColumnNames"
            multiple
            collapse-tags
            collapse-tags-tooltip
            placeholder="选择与源列数量一致的目标列"
          >
            <el-option
              v-for="column in relationTargetColumns"
              :key="column.columnName"
              :label="`${column.columnName} (${column.typeName || 'UNKNOWN'})`"
              :value="column.columnName"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="关系备注">
          <el-input
            v-model="relationForm.description"
            type="textarea"
            :rows="3"
            placeholder="可填写这条逻辑外键的业务说明"
          />
        </el-form-item>
        <el-form-item label="启用关系">
          <el-switch v-model="relationForm.enabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button
          @click="
            relationDialogVisible = false;
            resetRelationForm();
          "
        >
          取消
        </el-button>
        <el-button type="primary" :loading="relationSubmitLoading" @click="handleSubmitRelation">
          保存
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
  .semantic-model-page {
    display: flex;
    flex-direction: column;
    gap: 20px;
  }

  .hero-card,
  .toolbar-card,
  .content-card {
    background: #ffffff;
    border: 1px solid #e5e7eb;
    border-radius: 12px;
    box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
  }

  .hero-card {
    display: flex;
    justify-content: space-between;
    gap: 24px;
    padding: 24px 32px;
    background: linear-gradient(145deg, #ffffff 0%, #f9fafb 100%);
  }

  .hero-title {
    font-size: 22px;
    color: #1f2937;
    margin-bottom: 8px;
  }

  .hero-desc {
    max-width: 680px;
    color: #6b7280;
    line-height: 1.7;
  }

  .hero-meta {
    min-width: 180px;
    display: flex;
    flex-direction: column;
    justify-content: center;
    padding: 16px 20px;
    border-radius: 10px;
    background-color: #f9fafb;
    border: 1px solid #e5e7eb;
    color: #6b7280;
  }

  .hero-meta strong {
    margin-top: 6px;
    color: #1f2937;
    font-size: 16px;
  }

  .toolbar-card,
  .content-card {
    padding: 24px;
  }

  .content-toolbar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 16px;
    margin-bottom: 20px;
  }

  .content-toolbar-title {
    font-size: 16px;
    color: #1f2937;
    margin-bottom: 4px;
    font-weight: 600;
  }

  .content-toolbar-desc {
    color: #64748b;
    line-height: 1.6;
  }

  .content-toolbar-actions {
    display: flex;
    align-items: center;
    gap: 12px;
    flex-wrap: wrap;
  }

  .toolbar-grid {
    display: grid;
    grid-template-columns: minmax(220px, 280px) minmax(240px, 1fr) auto auto;
    gap: 16px;
    align-items: center;
  }

  .toolbar-field {
    width: 100%;
  }

  .toolbar-actions {
    display: flex;
    justify-content: flex-end;
  }

  .semantic-tabs :deep(.el-tabs__header) {
    margin-bottom: 24px;
  }

  .section-header {
    display: flex;
    justify-content: space-between;
    align-items: flex-start;
    gap: 16px;
    margin-bottom: 20px;
  }

  .section-header h3 {
    font-size: 16px;
    color: #1f2937;
    margin-bottom: 6px;
    font-weight: 600;
  }

  .section-header p {
    color: #64748b;
  }

  .section-header-actions {
    display: flex;
    align-items: center;
    gap: 12px;
  }

  .semantic-table {
    width: 100%;
  }

  .pagination-wrap {
    display: flex;
    justify-content: flex-end;
    margin-top: 20px;
  }

  .error-tip {
    margin-top: 14px;
    color: #dc2626;
  }

  .drawer-summary {
    display: flex;
    gap: 16px;
    margin-bottom: 20px;
    padding: 16px 20px;
    border-radius: 12px;
    background: #f9fafb;
    border: 1px solid #e5e7eb;
  }

  .summary-item {
    display: flex;
    flex-direction: column;
    gap: 6px;
    color: #6b7280;
  }

  .summary-item strong {
    color: #1f2937;
  }

  .domain-dialog-body {
    display: flex;
    flex-direction: column;
    gap: 16px;
  }

  .domain-dialog-tip {
    margin: 0;
    color: #64748b;
    line-height: 1.6;
    font-size: 13px;
  }

  @media (max-width: 1024px) {
    .hero-card {
      flex-direction: column;
    }

    .content-toolbar {
      flex-direction: column;
      align-items: flex-start;
    }

    .toolbar-grid {
      grid-template-columns: 1fr;
    }

    .toolbar-actions {
      justify-content: flex-start;
    }
  }
</style>
