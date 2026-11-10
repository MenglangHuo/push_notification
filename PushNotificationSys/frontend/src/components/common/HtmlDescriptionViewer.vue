<script setup lang="ts">
import { ref, computed } from 'vue'
import CodeMirror from 'vue-codemirror6'
import { html } from '@codemirror/lang-html'
import { useThemeStore } from '@/stores/themeStore'
import { Code } from 'lucide-vue-next'
import { sanitizeHtml } from '@/utils/security'

const props = withDefaults(
  defineProps<{
    content?: string | null
    emptyText?: string
    hideHeader?: boolean
    borderless?: boolean
  }>(),
  {
    content: '',
    emptyText: 'No description provided.',
    hideHeader: false,
    borderless: false,
  },
)

const themeStore = useThemeStore()
const viewMode = ref<'rendered' | 'source'>('rendered')
const htmlLang = html()

const rawHtml = computed(() => props.content || '')

// Safely sanitized HTML preventing Stored XSS
const sanitizedHtml = computed(() => sanitizeHtml(rawHtml.value))

// Check if content contains HTML tags
const isHtmlContent = computed(() => {
  if (!rawHtml.value) return false
  return /<[a-z][\s\S]*>/i.test(rawHtml.value)
})
</script>

<template>
  <div
    class="overflow-hidden"
    :class="
      borderless
        ? ''
        : 'rounded-2xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 shadow-2xs'
    "
  >
    <!-- Header Controls Bar -->
    <div
      v-if="!hideHeader"
      class="px-3.5 py-2 bg-slate-50/80 dark:bg-slate-950/70 border-b border-slate-200 dark:border-slate-800 flex items-center justify-between gap-2 text-xs"
    >
      <div class="flex items-center gap-2">
        <span
          class="text-[11px] font-bold uppercase tracking-wider text-slate-500 dark:text-slate-400 flex items-center gap-1.5"
        >
          <Code class="w-3.5 h-3.5 text-indigo-500" />
          <span>Description</span>
        </span>
      </div>
    </div>

    <!-- Main Content Area -->
    <div
      class="overflow-y-auto scrollbar-thin"
      :class="borderless ? 'p-1 max-h-[160px]' : 'p-4 max-h-[380px]'"
    >
      <!-- Empty State -->
      <div
        v-if="!rawHtml || !rawHtml.trim()"
        class="text-xs text-slate-400 dark:text-slate-500 italic py-4 text-center"
      >
        {{ emptyText }}
      </div>

      <!-- Mode 1: Rendered HTML (or formatted plain text) -->
      <div
        v-else-if="viewMode === 'rendered'"
        class="html-viewer-prose text-xs sm:text-sm text-slate-800 dark:text-slate-200 leading-relaxed"
      >
        <!-- If it has HTML tags, render via v-html with DOM sanitization -->
        <div v-if="isHtmlContent" v-html="sanitizedHtml"></div>
        <!-- If legacy plain text, render with whitespace-pre-wrap -->
        <div v-else class="whitespace-pre-wrap font-normal">{{ rawHtml }}</div>
      </div>

      <!-- Mode 2: CodeMirror 6 Source View (View-Only / Read-Only) -->
      <div
        v-else
        class="cm-readonly-viewer rounded-xl overflow-hidden border border-slate-200 dark:border-slate-800 bg-slate-50/60 dark:bg-slate-950/60"
      >
        <CodeMirror
          :model-value="rawHtml"
          :lang="htmlLang"
          :dark="themeStore.isDark"
          :basic="true"
          :wrap="true"
          :gutter="true"
          :readonly="true"
          class="text-xs font-mono min-h-[140px]"
        />
      </div>
    </div>
  </div>
</template>

<style>
/* CodeMirror Readonly Viewer Tweaks */
.cm-readonly-viewer .cm-editor {
  min-height: 140px;
  max-height: 340px;
  font-family:
    ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New',
    monospace;
  font-size: 0.8125rem;
  background: transparent !important;
}

.cm-readonly-viewer .cm-scroller {
  overflow: auto;
}

.cm-readonly-viewer .cm-content {
  padding: 8px 12px;
}

/* Styled HTML Viewer Prose */
.html-viewer-prose h1,
.html-viewer-prose h2,
.html-viewer-prose h3 {
  font-weight: 700;
  color: inherit;
  margin-top: 0.75rem;
  margin-bottom: 0.35rem;
}

.html-viewer-prose h1 {
  font-size: 1.15rem;
}
.html-viewer-prose h2 {
  font-size: 1.05rem;
}
.html-viewer-prose h3 {
  font-size: 0.95rem;
}

.html-viewer-prose p {
  margin-bottom: 0.5rem;
}

.html-viewer-prose strong,
.html-viewer-prose b {
  font-weight: 700;
}

.html-viewer-prose em,
.html-viewer-prose i {
  font-style: italic;
}

.html-viewer-prose ul {
  list-style-type: disc;
  padding-left: 1.25rem;
  margin-bottom: 0.5rem;
}

.html-viewer-prose ol {
  list-style-type: decimal;
  padding-left: 1.25rem;
  margin-bottom: 0.5rem;
}

.html-viewer-prose li {
  margin-bottom: 0.2rem;
}

.html-viewer-prose pre {
  background-color: rgba(15, 23, 42, 0.08);
  border-radius: 0.5rem;
  padding: 0.65rem 0.85rem;
  overflow-x: auto;
  font-family: ui-monospace, monospace;
  font-size: 0.775rem;
  margin: 0.5rem 0;
  border: 1px solid rgba(148, 163, 184, 0.2);
}

.dark .html-viewer-prose pre {
  background-color: rgba(2, 6, 23, 0.6);
  border-color: rgba(51, 65, 85, 0.6);
}

.html-viewer-prose code {
  background-color: rgba(99, 102, 241, 0.1);
  color: #4f46e5;
  padding: 0.15rem 0.35rem;
  border-radius: 0.25rem;
  font-family: ui-monospace, monospace;
  font-size: 0.85em;
}

.dark .html-viewer-prose code {
  background-color: rgba(99, 102, 241, 0.2);
  color: #a5b4fc;
}

.html-viewer-prose blockquote,
.html-viewer-prose .quote {
  border-left: 3px solid #6366f1;
  padding-left: 0.75rem;
  color: #64748b;
  margin: 0.5rem 0;
  font-style: italic;
}

.dark .html-viewer-prose blockquote,
.dark .html-viewer-prose .quote {
  color: #94a3b8;
}

.html-viewer-prose a {
  color: #4f46e5;
  text-decoration: underline;
}

.dark .html-viewer-prose a {
  color: #818cf8;
}

.html-viewer-prose table {
  width: 100%;
  border-collapse: collapse;
  margin: 0.5rem 0;
  font-size: 0.8rem;
}

.html-viewer-prose th,
.html-viewer-prose td {
  border: 1px solid rgba(148, 163, 184, 0.3);
  padding: 0.4rem 0.6rem;
  text-align: left;
}

.html-viewer-prose th {
  background-color: rgba(148, 163, 184, 0.1);
  font-weight: 600;
}
</style>
