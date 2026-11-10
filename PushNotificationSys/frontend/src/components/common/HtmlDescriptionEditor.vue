<script setup lang="ts">
import { useThemeStore } from '@/stores/themeStore'
import { isSafeUrl } from '@/utils/security'
import { html } from '@codemirror/lang-html'
import Link from '@tiptap/extension-link'
import Placeholder from '@tiptap/extension-placeholder'
import StarterKit from '@tiptap/starter-kit'
import { EditorContent, useEditor } from '@tiptap/vue-3'
import {
  Bold,
  Code,
  Code2,
  Eye,
  Heading1,
  Heading2,
  Heading3,
  Italic,
  Link as LinkIcon,
  List,
  ListOrdered,
  Quote,
  RotateCcw,
  RotateCw,
  Strikethrough,
  Trash2,
} from 'lucide-vue-next'
import { onBeforeUnmount, ref, watch } from 'vue'
import CodeMirror from 'vue-codemirror6'

const props = withDefaults(
  defineProps<{
    modelValue: string
    placeholder?: string
    minHeight?: string
    disabled?: boolean
  }>(),
  {
    modelValue: '',
    placeholder: 'Describe what happened, steps to reproduce, or requirements...',
    minHeight: '180px',
    disabled: false,
  },
)

const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void
}>()

const themeStore = useThemeStore()
const viewMode = ref<'visual' | 'code'>('visual')
const htmlLang = html()
const rawHtmlCode = ref(props.modelValue || '')

const editor = useEditor({
  content: props.modelValue || '',
  editable: !props.disabled,
  extensions: [
    StarterKit.configure({
      heading: {
        levels: [1, 2, 3],
      },
    }),
    Link.configure({
      openOnClick: false,
      HTMLAttributes: {
        class: 'text-indigo-600 dark:text-indigo-400 underline cursor-pointer',
        rel: 'noopener noreferrer',
        target: '_blank',
      },
    }),
    Placeholder.configure({
      placeholder: props.placeholder,
    }),
  ],
  onUpdate: () => {
    if (!editor.value) return
    const htmlContent = editor.value.getHTML()
    // Tiptap returns '<p></p>' when empty
    const normalized = htmlContent === '<p></p>' ? '' : htmlContent
    rawHtmlCode.value = normalized
    emit('update:modelValue', normalized)
  },
})

watch(
  () => props.modelValue,
  (newVal) => {
    if (!editor.value) return
    rawHtmlCode.value = newVal || ''
    const currentHTML = editor.value.getHTML()
    const normalizedCurrent = currentHTML === '<p></p>' ? '' : currentHTML
    if (newVal !== normalizedCurrent) {
      editor.value.commands.setContent(newVal || '', { emitUpdate: false })
    }
  },
)

watch(
  () => props.disabled,
  (newDisabled) => {
    if (editor.value) {
      editor.value.setEditable(!newDisabled)
    }
  },
)

function switchMode(mode: 'visual' | 'code') {
  if (mode === viewMode.value) return
  if (mode === 'code') {
    rawHtmlCode.value = editor.value ? editor.value.getHTML() : props.modelValue
    if (rawHtmlCode.value === '<p></p>') rawHtmlCode.value = ''
    viewMode.value = 'code'
  } else {
    // Switching back to visual
    if (editor.value) {
      editor.value.commands.setContent(rawHtmlCode.value || '', { emitUpdate: false })
    }
    viewMode.value = 'visual'
  }
}

function onCodeMirrorChange(val?: string | any) {
  const str = typeof val === 'string' ? val : val ? String(val) : ''
  rawHtmlCode.value = str
  emit('update:modelValue', str)
}

function setLink() {
  if (!editor.value) return
  const previousUrl = editor.value.getAttributes('link').href
  const url = window.prompt('Enter link URL (e.g. https://...):', previousUrl)
  if (url === null) return
  if (url === '') {
    editor.value.chain().focus().extendMarkRange('link').unsetLink().run()
    return
  }
  if (!isSafeUrl(url)) {
    alert('Invalid or unsafe link. Only http://, https://, mailto:, and tel: URLs are permitted.')
    return
  }
  editor.value.chain().focus().extendMarkRange('link').setLink({ href: url }).run()
}

function clearEditor() {
  if (confirm('Clear the description text?')) {
    if (editor.value) {
      editor.value.commands.setContent('', { emitUpdate: false })
    }
    rawHtmlCode.value = ''
    emit('update:modelValue', '')
  }
}

onBeforeUnmount(() => {
  editor.value?.destroy()
})
</script>

<template>
  <div
    class="rounded-2xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 overflow-hidden shadow-2xs focus-within:border-indigo-500/80 focus-within:ring-2 focus-within:ring-indigo-500/20 transition-all"
  >
    <!-- Rich Text Toolbar -->
    <div
      class="px-3 py-2 bg-slate-50/90 dark:bg-slate-950/80 border-b border-slate-200 dark:border-slate-800 flex flex-wrap items-center justify-between gap-2 text-xs select-none"
    >
      <!-- Formatting Buttons (Visible in Visual Mode) -->
      <div v-if="viewMode === 'visual'" class="flex items-center flex-wrap gap-0.5">
        <!-- Heading 1 -->
        <button
          type="button"
          @mousedown.prevent
          @click="editor?.chain().focus().toggleHeading({ level: 1 }).run()"
          :class="[
            editor?.isActive('heading', { level: 1 })
              ? 'bg-indigo-100 dark:bg-indigo-950/80 text-indigo-700 dark:text-indigo-300 font-bold'
              : 'text-slate-600 dark:text-slate-300 hover:text-slate-900 dark:hover:text-white hover:bg-slate-200/70 dark:hover:bg-slate-800',
          ]"
          class="p-1.5 rounded-lg transition-colors cursor-pointer"
          title="Large Heading (H1)"
        >
          <Heading1 class="w-3.5 h-3.5" />
        </button>

        <!-- Heading 2 -->
        <button
          type="button"
          @mousedown.prevent
          @click="editor?.chain().focus().toggleHeading({ level: 2 }).run()"
          :class="[
            editor?.isActive('heading', { level: 2 })
              ? 'bg-indigo-100 dark:bg-indigo-950/80 text-indigo-700 dark:text-indigo-300 font-bold'
              : 'text-slate-600 dark:text-slate-300 hover:text-slate-900 dark:hover:text-white hover:bg-slate-200/70 dark:hover:bg-slate-800',
          ]"
          class="p-1.5 rounded-lg transition-colors cursor-pointer"
          title="Medium Heading (H2)"
        >
          <Heading2 class="w-3.5 h-3.5" />
        </button>

        <!-- Heading 3 -->
        <button
          type="button"
          @mousedown.prevent
          @click="editor?.chain().focus().toggleHeading({ level: 3 }).run()"
          :class="[
            editor?.isActive('heading', { level: 3 })
              ? 'bg-indigo-100 dark:bg-indigo-950/80 text-indigo-700 dark:text-indigo-300 font-bold'
              : 'text-slate-600 dark:text-slate-300 hover:text-slate-900 dark:hover:text-white hover:bg-slate-200/70 dark:hover:bg-slate-800',
          ]"
          class="p-1.5 rounded-lg transition-colors cursor-pointer"
          title="Small Heading (H3)"
        >
          <Heading3 class="w-3.5 h-3.5" />
        </button>

        <div class="h-3.5 w-px bg-slate-200 dark:bg-slate-800 mx-1"></div>

        <!-- Bold -->
        <button
          type="button"
          @mousedown.prevent
          @click="editor?.chain().focus().toggleBold().run()"
          :class="[
            editor?.isActive('bold')
              ? 'bg-indigo-100 dark:bg-indigo-950/80 text-indigo-700 dark:text-indigo-300 font-bold'
              : 'text-slate-600 dark:text-slate-300 hover:text-slate-900 dark:hover:text-white hover:bg-slate-200/70 dark:hover:bg-slate-800',
          ]"
          class="p-1.5 rounded-lg transition-colors cursor-pointer"
          title="Bold (Ctrl+B)"
        >
          <Bold class="w-3.5 h-3.5" />
        </button>

        <!-- Italic -->
        <button
          type="button"
          @mousedown.prevent
          @click="editor?.chain().focus().toggleItalic().run()"
          :class="[
            editor?.isActive('italic')
              ? 'bg-indigo-100 dark:bg-indigo-950/80 text-indigo-700 dark:text-indigo-300 font-bold'
              : 'text-slate-600 dark:text-slate-300 hover:text-slate-900 dark:hover:text-white hover:bg-slate-200/70 dark:hover:bg-slate-800',
          ]"
          class="p-1.5 rounded-lg transition-colors cursor-pointer"
          title="Italic (Ctrl+I)"
        >
          <Italic class="w-3.5 h-3.5" />
        </button>

        <!-- Strike -->
        <button
          type="button"
          @mousedown.prevent
          @click="editor?.chain().focus().toggleStrike().run()"
          :class="[
            editor?.isActive('strike')
              ? 'bg-indigo-100 dark:bg-indigo-950/80 text-indigo-700 dark:text-indigo-300 font-bold'
              : 'text-slate-600 dark:text-slate-300 hover:text-slate-900 dark:hover:text-white hover:bg-slate-200/70 dark:hover:bg-slate-800',
          ]"
          class="p-1.5 rounded-lg transition-colors cursor-pointer"
          title="Strikethrough"
        >
          <Strikethrough class="w-3.5 h-3.5" />
        </button>

        <div class="h-3.5 w-px bg-slate-200 dark:bg-slate-800 mx-1"></div>

        <!-- Bullet List -->
        <button
          type="button"
          @mousedown.prevent
          @click="editor?.chain().focus().toggleBulletList().run()"
          :class="[
            editor?.isActive('bulletList')
              ? 'bg-indigo-100 dark:bg-indigo-950/80 text-indigo-700 dark:text-indigo-300 font-bold'
              : 'text-slate-600 dark:text-slate-300 hover:text-slate-900 dark:hover:text-white hover:bg-slate-200/70 dark:hover:bg-slate-800',
          ]"
          class="p-1.5 rounded-lg transition-colors cursor-pointer"
          title="Bullet List"
        >
          <List class="w-3.5 h-3.5" />
        </button>

        <!-- Numbered List -->
        <button
          type="button"
          @mousedown.prevent
          @click="editor?.chain().focus().toggleOrderedList().run()"
          :class="[
            editor?.isActive('orderedList')
              ? 'bg-indigo-100 dark:bg-indigo-950/80 text-indigo-700 dark:text-indigo-300 font-bold'
              : 'text-slate-600 dark:text-slate-300 hover:text-slate-900 dark:hover:text-white hover:bg-slate-200/70 dark:hover:bg-slate-800',
          ]"
          class="p-1.5 rounded-lg transition-colors cursor-pointer"
          title="Numbered List"
        >
          <ListOrdered class="w-3.5 h-3.5" />
        </button>

        <!-- Blockquote -->
        <button
          type="button"
          @mousedown.prevent
          @click="editor?.chain().focus().toggleBlockquote().run()"
          :class="[
            editor?.isActive('blockquote')
              ? 'bg-indigo-100 dark:bg-indigo-950/80 text-indigo-700 dark:text-indigo-300 font-bold'
              : 'text-slate-600 dark:text-slate-300 hover:text-slate-900 dark:hover:text-white hover:bg-slate-200/70 dark:hover:bg-slate-800',
          ]"
          class="p-1.5 rounded-lg transition-colors cursor-pointer"
          title="Blockquote"
        >
          <Quote class="w-3.5 h-3.5" />
        </button>

        <!-- Code Block -->
        <button
          type="button"
          @mousedown.prevent
          @click="editor?.chain().focus().toggleCodeBlock().run()"
          :class="[
            editor?.isActive('codeBlock')
              ? 'bg-indigo-100 dark:bg-indigo-950/80 text-indigo-700 dark:text-indigo-300 font-bold'
              : 'text-slate-600 dark:text-slate-300 hover:text-slate-900 dark:hover:text-white hover:bg-slate-200/70 dark:hover:bg-slate-800',
          ]"
          class="p-1.5 rounded-lg transition-colors cursor-pointer"
          title="Code Block"
        >
          <Code class="w-3.5 h-3.5" />
        </button>

        <!-- Link -->
        <button
          type="button"
          @mousedown.prevent
          @click="setLink"
          :class="[
            editor?.isActive('link')
              ? 'bg-indigo-100 dark:bg-indigo-950/80 text-indigo-700 dark:text-indigo-300 font-bold'
              : 'text-slate-600 dark:text-slate-300 hover:text-slate-900 dark:hover:text-white hover:bg-slate-200/70 dark:hover:bg-slate-800',
          ]"
          class="p-1.5 rounded-lg transition-colors cursor-pointer"
          title="Insert Link"
        >
          <LinkIcon class="w-3.5 h-3.5" />
        </button>

        <div class="h-3.5 w-px bg-slate-200 dark:bg-slate-800 mx-1"></div>

        <!-- Undo -->
        <button
          type="button"
          @mousedown.prevent
          @click="editor?.chain().focus().undo().run()"
          :disabled="!editor?.can().undo()"
          class="p-1.5 rounded-lg text-slate-500 dark:text-slate-400 hover:text-slate-900 dark:hover:text-white hover:bg-slate-200/70 dark:hover:bg-slate-800 disabled:opacity-30 disabled:pointer-events-none transition-colors cursor-pointer"
          title="Undo (Ctrl+Z)"
        >
          <RotateCcw class="w-3.5 h-3.5" />
        </button>

        <!-- Redo -->
        <button
          type="button"
          @mousedown.prevent
          @click="editor?.chain().focus().redo().run()"
          :disabled="!editor?.can().redo()"
          class="p-1.5 rounded-lg text-slate-500 dark:text-slate-400 hover:text-slate-900 dark:hover:text-white hover:bg-slate-200/70 dark:hover:bg-slate-800 disabled:opacity-30 disabled:pointer-events-none transition-colors cursor-pointer"
          title="Redo (Ctrl+Y)"
        >
          <RotateCw class="w-3.5 h-3.5" />
        </button>
      </div>

      <!-- Code Mode Toolbar Label -->
      <div v-else class="flex items-center gap-1.5 text-xs text-slate-500 dark:text-slate-400">
        <Code2 class="w-4 h-4 text-indigo-500" />
        <span class="font-semibold text-slate-700 dark:text-slate-300">HTML Source Code</span>
        <span class="text-[10px] text-slate-400 font-mono">(Developer Mode)</span>
      </div>

      <!-- Right Toolbar Tools: Clear & Mode Toggle (Visual vs HTML Code) -->
      <div class="flex items-center gap-1.5 ml-auto">
        <button
          v-if="modelValue"
          type="button"
          @click="clearEditor"
          class="p-1 rounded-md text-slate-400 hover:text-rose-500 hover:bg-rose-50 dark:hover:bg-rose-500/10 transition-colors cursor-pointer"
          title="Clear content"
        >
          <Trash2 class="w-3.5 h-3.5" />
        </button>

        <div
          class="flex items-center p-0.5 rounded-lg bg-slate-200/80 dark:bg-slate-800 text-[11px] font-medium border border-slate-300/80 dark:border-slate-700/60"
        >
          <button
            type="button"
            @click="switchMode('visual')"
            class="flex items-center gap-1 px-2.5 py-0.5 rounded-md transition-all cursor-pointer"
            :class="
              viewMode === 'visual'
                ? 'bg-white dark:bg-slate-700 text-indigo-600 dark:text-indigo-400 shadow-2xs font-bold'
                : 'text-slate-600 dark:text-slate-400 hover:text-slate-900 dark:hover:text-white'
            "
            title="Visual Rich Text Editor"
          >
            <Eye class="w-3 h-3" />
            <span>Visual</span>
          </button>
          <button
            type="button"
            @click="switchMode('code')"
            class="flex items-center gap-1 px-2.5 py-0.5 rounded-md transition-all cursor-pointer"
            :class="
              viewMode === 'code'
                ? 'bg-white dark:bg-slate-700 text-indigo-600 dark:text-indigo-400 shadow-2xs font-bold'
                : 'text-slate-600 dark:text-slate-400 hover:text-slate-900 dark:hover:text-white'
            "
            title="Raw HTML Source Editor"
          >
            <Code2 class="w-3 h-3" />
            <span>HTML</span>
          </button>
        </div>
      </div>
    </div>

    <!-- Main Editor Body -->
    <div
      class="relative bg-white dark:bg-slate-900"
      :style="{ minHeight: props.minHeight, '--editor-min-height': props.minHeight }"
    >
      <!-- Visual Mode: Tiptap Editor (WYSIWYG, No HTML tags displayed) -->
      <div v-show="viewMode === 'visual'" class="rich-tiptap-wrapper">
        <EditorContent
          :editor="editor"
          class="text-xs sm:text-sm text-slate-800 dark:text-slate-200"
        />
      </div>

      <!-- Code Mode: CodeMirror 6 Source Editor -->
      <div
        v-show="viewMode === 'code'"
        class="cm-editor-container bg-slate-50/40 dark:bg-slate-950/40"
      >
        <CodeMirror
          :model-value="rawHtmlCode"
          @update:model-value="onCodeMirrorChange"
          :lang="htmlLang"
          :dark="themeStore.isDark"
          :basic="true"
          :wrap="true"
          :gutter="true"
          :placeholder="placeholder"
          class="text-xs font-mono"
        />
      </div>
    </div>

    <!-- Bottom Status Bar -->
    <div
      class="px-3 py-1.5 bg-slate-50/70 dark:bg-slate-950/60 border-t border-slate-100 dark:border-slate-800/80 text-[11px] text-slate-400 dark:text-slate-500 flex items-center justify-between"
    >
      <span class="text-[10px] font-medium text-slate-500 dark:text-slate-400">
        {{
          viewMode === 'visual'
            ? 'Rich Text Editor (formats selected text)'
            : 'HTML Source (vue-codemirror6)'
        }}
      </span>
      <span>{{ (modelValue || '').length }} chars</span>
    </div>
  </div>
</template>

<style>
/* Tiptap / ProseMirror Visual Editor Styles */
.rich-tiptap-wrapper .ProseMirror {
  outline: none;
  min-height: var(--editor-min-height, 180px);
  max-height: 560px;
  overflow-y: auto;
  padding: 12px 14px;
  line-height: 1.6;
}

.rich-tiptap-wrapper .ProseMirror p.is-editor-empty:first-child::before {
  content: attr(data-placeholder);
  float: left;
  color: #94a3b8;
  pointer-events: none;
  height: 0;
  font-style: normal;
}

.rich-tiptap-wrapper .ProseMirror h1 {
  font-size: 1.25rem;
  font-weight: 700;
  margin-top: 0.75rem;
  margin-bottom: 0.35rem;
  color: inherit;
}

.rich-tiptap-wrapper .ProseMirror h2 {
  font-size: 1.1rem;
  font-weight: 700;
  margin-top: 0.65rem;
  margin-bottom: 0.3rem;
  color: inherit;
}

.rich-tiptap-wrapper .ProseMirror h3 {
  font-size: 1rem;
  font-weight: 700;
  margin-top: 0.55rem;
  margin-bottom: 0.25rem;
  color: inherit;
}

.rich-tiptap-wrapper .ProseMirror p {
  margin-bottom: 0.5rem;
}

.rich-tiptap-wrapper .ProseMirror strong,
.rich-tiptap-wrapper .ProseMirror b {
  font-weight: 700;
}

.rich-tiptap-wrapper .ProseMirror em,
.rich-tiptap-wrapper .ProseMirror i {
  font-style: italic;
}

.rich-tiptap-wrapper .ProseMirror s,
.rich-tiptap-wrapper .ProseMirror strike {
  text-decoration: line-through;
}

.rich-tiptap-wrapper .ProseMirror ul {
  list-style-type: disc;
  padding-left: 1.25rem;
  margin-bottom: 0.5rem;
}

.rich-tiptap-wrapper .ProseMirror ol {
  list-style-type: decimal;
  padding-left: 1.25rem;
  margin-bottom: 0.5rem;
}

.rich-tiptap-wrapper .ProseMirror li {
  margin-bottom: 0.2rem;
}

.rich-tiptap-wrapper .ProseMirror pre {
  background-color: rgba(15, 23, 42, 0.08);
  border-radius: 0.5rem;
  padding: 0.65rem 0.85rem;
  overflow-x: auto;
  font-family: ui-monospace, monospace;
  font-size: 0.8rem;
  margin: 0.5rem 0;
  border: 1px solid rgba(148, 163, 184, 0.2);
}

.dark .rich-tiptap-wrapper .ProseMirror pre {
  background-color: rgba(2, 6, 23, 0.6);
  border-color: rgba(51, 65, 85, 0.6);
}

.rich-tiptap-wrapper .ProseMirror code {
  background-color: rgba(99, 102, 241, 0.1);
  color: #4f46e5;
  padding: 0.15rem 0.35rem;
  border-radius: 0.25rem;
  font-family: ui-monospace, monospace;
  font-size: 0.875em;
}

.dark .rich-tiptap-wrapper .ProseMirror code {
  background-color: rgba(99, 102, 241, 0.2);
  color: #a5b4fc;
}

.rich-tiptap-wrapper .ProseMirror blockquote {
  border-left: 3px solid #6366f1;
  padding-left: 0.75rem;
  color: #64748b;
  margin: 0.5rem 0;
  font-style: italic;
}

.dark .rich-tiptap-wrapper .ProseMirror blockquote {
  color: #94a3b8;
}

.rich-tiptap-wrapper .ProseMirror a {
  color: #4f46e5;
  text-decoration: underline;
}

.dark .rich-tiptap-wrapper .ProseMirror a {
  color: #818cf8;
}

/* CodeMirror Container */
.cm-editor-container .cm-editor {
  min-height: var(--editor-min-height, 180px);
  max-height: 560px;
  font-family:
    ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New',
    monospace;
  font-size: 0.8125rem;
  background: transparent !important;
}

.cm-editor-container .cm-scroller {
  overflow: auto;
}

.cm-editor-container .cm-content {
  padding: 10px 14px;
}
</style>
